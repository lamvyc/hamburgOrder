# 阶段 B1 排错入口

学习故障练习在 [阶段 B1 学习材料](../learning/stage-b1.md)。

## 1. 领券 409

| code | 先查 |
| --- | --- |
| `COUPON_ALREADY_CLAIMED` | `user_coupons(template_id,user_id)` 是否已存在 |
| `COUPON_SOLD_OUT` | `claimed_quantity` 是否等于 `total_quantity` |
| `COUPON_NOT_AVAILABLE` | status/valid_from/valid_to |

```sql
SELECT id, code, status, total_quantity, claimed_quantity,
       valid_from, valid_to
FROM coupon_templates WHERE id=?;

SELECT id, template_id, user_id, status, order_id
FROM user_coupons WHERE template_id=? AND user_id=?;
```

“剩余显示 0”与“接口仍返回 SOLD_OUT”可能只是同一个事实；但若剩余 >0 仍 SOLD_OUT，检查条件更新是否包含 status/有效期。

## 2. 下单 COUPON_NOT_USABLE

- 券不属于当前用户：应返回 `COUPON_NOT_FOUND`；
- 券是 LOCKED/USED；
- 模板停用/过期；
- 已经被另一笔并发订单锁定。

```sql
SELECT uc.id, uc.status, uc.order_id, ct.status, ct.valid_from, ct.valid_to
FROM user_coupons uc JOIN coupon_templates ct ON ct.id=uc.template_id
WHERE uc.id=?;
```

## 3. COUPON_MIN_ORDER_NOT_MET

服务端按商品行金额之和判断门槛，不按客户端本地小计。检查：

```sql
SELECT order_id, SUM(line_amount) FROM order_items WHERE order_id=?;
```

## 4. 订单金额不对

不变量：

```text
商品小计 = SUM(order_items.line_amount)
应付 = 商品小计 - discount_amount
```

如果客户端传优惠金额，服务端应忽略；本项目根本不接收客户端金额。

## 5. 券卡在 LOCKED

- 订单创建事务失败后是否整体回滚？
- 订单被取消/超时后是否调用 `couponService.release`？
- `release` 条件是否 `id + order_id + status='LOCKED'`？
- 是否有人绕过 Service 直接改订单状态？

```sql
SELECT o.id,o.status,o.coupon_id,uc.id,uc.status,uc.order_id
FROM orders o LEFT JOIN user_coupons uc ON uc.id=o.coupon_id
WHERE o.id=?;
```

## 6. 并发领取/锁券

- 领取：检查模板条件自增与用户唯一键是否在同一事务；
- 下单：检查订单插入后锁券失败是否抛异常回滚；
- 不要只看接口 200/409，要看模板 claimed_quantity、用户券数量、订单数量、库存终态。

## 7. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:b1
```

B1 E2E 会领取优惠券、下单优惠并在结束时取消释放，保证可重复。
