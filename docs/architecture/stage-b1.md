# 阶段 B1 架构：优惠券领取与核销

## 1. 目标

B1 在 A5 的订单/库存/取消闭环上增加：

1. 顾客查看可领取优惠券。
2. 领取固定金额券，单用户限领 1 张。
3. 总量防超发：`claimed_quantity` 条件增加。
4. 下单锁券，订单记录优惠金额。
5. 支付核销优惠券。
6. 取消/超时释放回 UNUSED。
7. 重复领取、重复使用、越权、未达门槛明确报错。
8. 前端购物车领券、选券、优惠预览；订单详情显示优惠。

## 2. 数据模型

```text
coupon_templates
  1 ─── n user_coupons
              │
              └── order_id (LOCKED/USED 时关联订单)
```

- `coupon_templates`：面额、门槛、总量、已领量、有效期、状态、版本。
- `user_coupons`：用户券实例，状态 UNUSED/LOCKED/USED。
- `orders.coupon_id`：下单锁定的用户券；`orders.discount_amount`：成交优惠。
- 单用户限领 1 张由 `UNIQUE (template_id,user_id)` 保证；`per_user_limit` 当前固定 1。

## 3. 领取流程

```text
POST /api/coupons/{templateId}/claim
  -> 校验模板 ACTIVE + 有效期
  -> 检查用户是否已领取
  -> UPDATE coupon_templates
       SET claimed_quantity = claimed_quantity + 1
     WHERE id=? AND status='ACTIVE'
       AND claimed_quantity < total_quantity
       AND valid_from <= now AND valid_to >= now
  -> affected rows == 1
  -> INSERT user_coupons UNUSED
  -> 唯一键 (template_id,user_id) 防重复
```

两道闸门：

1. 总量：条件 UPDATE，最后一张券只有一个请求能成功。
2. 单用户：唯一键；同用户并发领取时，失败者的模板自增也随事务回滚。

领取事务中任何失败都不会留下“已增加 claimed_quantity 但没有用户券”的半状态。

## 4. 下单锁券

```text
OrderService.create(request.userCouponId)
  -> 计算商品 subtotal
  -> 校验券：owner / UNUSED / ACTIVE / 有效期 / 门槛
  -> discount = min(coupon.discount, subtotal)
  -> INSERT orders (coupon_id, discount_amount, total_amount=subtotal-discount)
  -> UPDATE user_coupons
       SET status='LOCKED', order_id=?
     WHERE id=? AND user_id=? AND status='UNUSED'
  -> affected rows == 1
  -> INSERT order_items / 预占库存 / 事件 / 幂等完成
```

锁券与订单、明细、库存、事件、幂等记录同一事务。库存失败或订单失败会回滚券状态。

两个并发订单使用同一张券：一个锁成功；另一个 affected=0 → 409 `COUPON_NOT_USABLE`，其订单插入回滚。

## 5. 支付核销

支付成功事务中：

```sql
UPDATE user_coupons
SET status='USED', used_at=?, version=version+1
WHERE id=? AND order_id=? AND status='LOCKED';
```

支付状态、库存 COMMIT、券核销、事件必须同时成功；失败整体回滚。

## 6. 取消/超时释放

取消或超时释放库存后：

```sql
UPDATE user_coupons
SET status='UNUSED', order_id=NULL, used_at=NULL, version=version+1
WHERE id=? AND order_id=? AND status='LOCKED';
```

- 顾客取消：券释放；
- 超时扫描：券释放；
- 重复取消：订单条件更新失败，不会再次释放券；
- 已支付订单不能取消。

## 7. 优惠金额

- 券为固定金额；`discount = min(faceValue, subtotal)`。
- 下单响应 `discountAmount` 与 `totalAmount`。
- 订单明细保留成交单价与行金额，优惠在订单级。
- 不支持百分比、叠加、分配优惠到行项目。

## 8. 前端

购物车：

- 可领取模板 → 领取；
- 我的 UNUSED 券 → 选择/取消；
- 低于门槛的券禁用；
- 小计、优惠、应付；
- 提交时传 `userCouponId`。

订单详情：

- 商品小计、优惠、应付；
- 取消后券释放，重新可用。

## 9. 并发边界

已实现/验证：

- 最后一张券两用户并发领取：一成功一 `COUPON_SOLD_OUT`；
- 同用户并发领取：一成功一 `COUPON_ALREADY_CLAIMED`；
- 同一券并发下两单：只有一单锁券成功；
- 订单失败回滚券锁。

未实现：

- 百分比券与舍入；
- 多券叠加/互斥；
- 券即将过期提醒；
- 券转赠/退回；
- 管理端发券/作废；
- 领取限流。
