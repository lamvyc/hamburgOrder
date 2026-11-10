# 阶段 A5 架构与竞态

## 1. 目标

A5 完成阶段 A 的最后闭环：

1. 顾客取消本人未支付订单。
2. 超时订单由后台分批扫描取消。
3. 取消释放 reserved → available。
4. 状态、库存、库存流水、订单事件同一事务。
5. 重复取消/重复扫描不重复释放。
6. 支付与取消竞争只允许一个有效转换。
7. 员工/其他顾客不能取消不属于自己的订单。
8. 前端展示取消按钮、CANCELLED 和事件轨迹。

优惠券尚未实现，因此本轮没有“优惠券释放”；文档明确说明而不是伪造。

## 2. 取消事务

```text
POST /api/orders/{id}/cancel
  -> 校验 current user 拥有订单
  -> 校验 status = WAIT_PAY
  -> UPDATE orders SET status='CANCELLED'
       WHERE id=? AND user_id=? AND status='WAIT_PAY'
  -> affected rows == 1
  -> for each order_item sorted by product_id:
       UPDATE stock
         SET available=available+qty,
             reserved=reserved-qty,
             version=version+1
       WHERE store_id=? AND product_id=? AND reserved>=qty
       affected == 1
       stock_logs RELEASE
  -> order_events CANCELLED (operator=customer)
```

订单状态更新是并发闸门。输掉竞争的请求不会释放库存。

## 3. 超时扫描

V9 增加：

```sql
CREATE INDEX idx_orders_status_expires
    ON orders (status, expires_at, id);
```

扫描：

```sql
SELECT id FROM orders
WHERE status='WAIT_PAY' AND expires_at <= ?
ORDER BY expires_at, id
LIMIT ?;
```

每个订单由 `OrderCancellationService.cancelExpired()` 在 **REQUIRES_NEW** 事务中处理：

- `UPDATE ... WHERE status='WAIT_PAY' AND expires_at <= cutoff`；
- affected 0：支付/手动取消/其他实例已处理，跳过；
- affected 1：释放库存、写 RELEASE、写 CANCELLED 事件（operator NULL，reason=支付超时自动取消）；
- 单个订单失败不会回滚整个批次。

`scheduler` 初始延迟 30s、间隔 60s、批大小 100，可用配置覆盖。测试直接传入过期数据和 cutoff，不实际等待 30 分钟。

## 4. 支付/取消竞态

支付：

```sql
UPDATE orders SET status='WAIT_MAKE'
WHERE id=? AND user_id=? AND status='WAIT_PAY' AND expires_at > now;
```

取消：

```sql
UPDATE orders SET status='CANCELLED'
WHERE id=? AND user_id=? AND status='WAIT_PAY';
```

两者都在同一订单行上做条件更新：

- 支付赢：只有 payment 分支写 COMMIT 流水与 PAYMENT_SUCCEEDED；
- 取消赢：只有 cancel 分支写 RELEASE 与 CANCELLED；
- 失败一方 affected=0 → 409；
- 不会出现“已支付又释放库存”或“已取消又提交 reserved”。

集成测试用两个线程同时支付/取消，最终状态只允许 WAIT_MAKE 或 CANCELLED，库存终态与唯一终态流水一致。

## 5. 重复扫描

第一次扫描把订单改为 CANCELLED 并释放库存；第二次扫描查询不到 WAIT_PAY 订单，返回 0。

即使两个扫描器同时拿到同一 ID，条件更新也只有一条 affected=1；另一个返回 false，不释放第二次。

## 6. 权限

- `POST /api/orders/*/cancel`：CUSTOMER；
- 订单 user_id 必须等于当前 principal；
- 员工取消顾客订单 → 403；
- 其他顾客取消 → 404，不泄露订单存在；
- 已支付/已完成订单取消 → 409。

## 7. 事件

A5 实际事件：

```text
CANCELLED  WAIT_PAY -> CANCELLED  操作者=顾客     reason=顾客取消订单
CANCELLED  WAIT_PAY -> CANCELLED  操作者=NULL     reason=支付超时自动取消
```

订单详情展示统一轨迹，顾客可以看到取消原因与操作者。

## 8. 前端

订单详情在 WAIT_PAY 时显示：

- 模拟支付；
- 取消订单。

取消弹窗说明会释放库存；成功后订单状态变为 CANCELLED，事件轨迹刷新；刷新页面仍能读取。

## 9. 边界与后续

本轮没有：

- 优惠券，故无优惠券释放；
- 退款；
- 已支付订单取消；
- 超时扫描指标；
- 多实例 Session/任务协调；
- 消息/Outbox。

Phase B 再处理库存超卖复现、幂等并发、优惠券防超发、死锁/重试、游标分页、管理端和更细的执行计划。
