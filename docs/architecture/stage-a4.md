# 阶段 A4 架构与状态机

## 1. 目标

A4 实现：

1. 顾客对本人未支付、未过期订单做模拟支付。
2. `WAIT_PAY → WAIT_MAKE`。
3. 员工只查看所属门店的活跃订单。
4. 员工完成 `WAIT_MAKE → MAKING → READY → COMPLETED`。
5. 每次转换条件更新、检查影响行数、同一事务写事件。
6. 顾客查看订单事件轨迹。
7. 重复/非法/越权/过期返回明确业务错误。

不实现取消、超时扫描、支付回调、退款。

## 2. 状态机

```text
WAIT_PAY --pay--> WAIT_MAKE --start--> MAKING --ready--> READY --complete--> COMPLETED
```

没有单独的“已接单”状态。

非法示例：

- `WAIT_PAY --start-->` 409；
- `WAIT_MAKE --complete-->` 409；
- 已支付订单再次支付 409；
- 过期 `WAIT_PAY` 支付 409；
- 员工操作其他门店订单 404；
- 顾客调用员工接口 403。

## 3. 支付事务

`POST /api/orders/{id}/pay`：

```text
校验 current user 拥有订单
校验 status = WAIT_PAY
校验 expires_at > now
UPDATE orders SET status='WAIT_MAKE'
  WHERE id=? AND user_id=? AND status='WAIT_PAY' AND expires_at > now
检查 affected rows = 1
for each order_item sorted by product_id:
  UPDATE stock SET reserved=reserved-qty
    WHERE store_id=? AND product_id=? AND reserved>=qty
  检查 affected rows = 1
  写 stock_logs(change_type='COMMIT', available_delta=0, reserved_delta=-qty)
写 order_events(PAYMENT_SUCCEEDED)
```

Why 不再次扣 `available`：下单时 `available` 已经减少、`reserved` 增加；支付只是把预占变为已消费，所以只减少 `reserved`。

Why 条件里带 `expires_at > now`：即使 A5 扫描任务还没运行，过期订单也不能支付。支付失败不修改订单；A5 负责取消并释放库存。

## 4. 员工状态转换

`StaffOrderService`：

- 读取订单；
- 按 principal 的 user id 检查门店 membership；
- 条件更新 `(id, store_id, from_status)`；
- affected rows ≠ 1 → 409 `ORDER_STATUS_CONFLICT`；
- 同一事务写事件；
- 返回更新后的 OrderResponse。

员工数据范围不是前端门店参数决定的。`GET /api/staff/orders?storeId=` 也先校验 membership。

## 5. 事件轨迹

`order_events` 保存：

- event_type；
- from_status / to_status；
- operator_user_id；
- reason；
- created_at。

A4 实际事件：

```text
CREATED
PAYMENT_SUCCEEDED
MAKING_STARTED
READY_FOR_PICKUP
COMPLETED
```

顾客通过 `GET /api/orders/{id}/events` 查看本人订单轨迹；事件查询 LEFT JOIN users 得到操作者展示名。

## 6. 索引与执行计划

V8 增加：

```sql
CREATE INDEX idx_orders_store_status_created
    ON orders (store_id, status, created_at, id);
```

员工活跃订单查询：

```sql
SELECT * FROM orders
WHERE store_id=1
  AND status IN ('WAIT_MAKE','MAKING','READY')
ORDER BY created_at, id;
```

实际 `EXPLAIN`：

```text
key=idx_orders_store_status_created
key_len=74
rows=3
Extra=Using index condition; Using filesort
```

需要如实说明：`status IN` 跨多个状态，MySQL 仍需要 filesort。当前活跃订单只有 3 条，先保留索引与 filesort；如果后续活跃量增长，应重新评估索引或按状态分组查询，不能因为“用了索引”就宣称排序已完全索引化。

## 7. 为什么没有 payments 表

A4 是模拟支付，不是支付回调实验：

- 没有第三方交易号；
- 没有异步回调签名/幂等；
- 没有退款、对账。

支付结果以订单状态 + `PAYMENT_SUCCEEDED` 事件 + 库存 COMMIT 流水证明。等引入支付回调实验时再设计 `payments` 表，避免现在造一张没有真实回调语义的空表。

## 8. 事务/并发边界

已测：

- 两线程同时支付同一订单：仅一次成功，库存只 COMMIT 一次，事件只有一条 PAYMENT_SUCCEEDED；
- 重复支付串行：第二次 409；
- 过期支付：409，订单保持 WAIT_PAY，reserved 不释放（A5）；
- 多商品支付提交按 productId 升序。

未测：

- 支付与取消竞态（A5）；
- 员工重复点击的并发双请求（条件更新已具备，但未单独压测）；
- 多实例、消息/回调幂等。

## 9. 前端

- 订单详情：A4 模拟支付按钮、更新后的状态、事件轨迹。
- 员工工作台：门店 tab、活跃订单、按状态显示“开始制作/标记待取餐/完成订单”。
- 支付/工作台错误显示 traceId。
- 顾客与员工路由仍由前端守卫改善体验，后端 URL 角色与 membership 才有效。

## 10. A5 边界

A5 负责：

- 顾客取消未支付订单；
- 超时扫描；
- 取消/超时释放 reserved + available；
- 支付与取消竞态；
- 重复扫描不重复释放；
- 取消/超时事件。
