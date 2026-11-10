# 阶段 A3 排错入口

学习故障练习在 [阶段 A3 学习材料](../learning/stage-a3.md)。

## 1. 下单失败先看状态码与 code

| HTTP | code | 入口 |
| --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` / `INVALID_IDEMPOTENCY_KEY` | 请求头 |
| 400 | `DUPLICATE_ORDER_ITEM` | 请求 items |
| 401 | `UNAUTHORIZED` | Session |
| 403 | `FORBIDDEN` / `CSRF_INVALID` | 角色/CSRF |
| 409 | `STOCK_INSUFFICIENT` | stock |
| 409 | `STORE_NOT_ORDERABLE` | stores/business |
| 409 | `PRODUCT_UNAVAILABLE` | products 门店/上架 |
| 409 | `IDEMPOTENCY_CONFLICT` | 幂等键/请求体 |
| 503 | `DATABASE_UNAVAILABLE` | 数据库连接 |

不要把所有 409 都归因于库存。

## 2. 查库存

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT store_id, product_id, available, reserved, version
      FROM stock WHERE product_id IN (1001,1006);"
```

- `available` 是未预占库存；
- `reserved` 是已下单未支付/未完成释放的库存；
- `version` 每次成功变化递增。

如果 API 报库存不足但 `available` 看起来足够：

1. 检查 `store_id` 与 `product_id` 组合；
2. 检查商品是否属于该门店；
3. 检查并发请求是否已经先扣减；
4. 检查事务是否回滚；
5. 查看 traceId 对应日志。

## 3. 查订单/明细/流水

```sql
SELECT id, order_no, user_id, store_id, status, total_amount, expires_at
FROM orders ORDER BY id DESC LIMIT 10;

SELECT order_id, product_id, product_name, unit_price, quantity, line_amount
FROM order_items WHERE order_id = ?;

SELECT product_id, change_type, available_delta, reserved_delta,
       available_after, reserved_after, reason
FROM stock_logs WHERE order_id = ?;
```

三者应当一致：订单金额 = 明细行金额之和，库存流水 deltas 与 stock 最终值一致。

## 4. 查幂等记录

```sql
SELECT user_id, operation, idempotency_key, request_hash, status, order_id
FROM idempotency_records
ORDER BY id DESC LIMIT 10;
```

- 同 key 同 hash 应返回原订单；
- 同 key 不同 hash -> `IDEMPOTENCY_CONFLICT`；
- 失败事务不应留下 PROCESSING 记录；
- 如果两个订单对应两个不同 key，前端幂等键生成/持久化有问题。

浏览器检查：

- localStorage `burger-order.checkout.v1`；
- Network 请求头 `Idempotency-Key`；
- 两次点击是否是同一个 key。

## 5. 订单 404

- 是否已登录 CUSTOMER；
- 订单 `user_id` 是否等于当前用户；
- 是否请求了别人的订单（设计上统一 404）；
- 员工/管理员访问顾客订单接口应得到 403。

## 6. 订单创建成功但页面没跳转

- Network 是否已返回 201/200；
- 前端是否收到 `id`；
- 路由 `/orders/{id}` 是否存在；
- 浏览器是否有未捕获异常；
- 如果请求超时但服务端已成功，重试同 key 应返回 200 重放并清空购物车。

## 7. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:a3
```

A3 并发测试必须看到真实 MySQL 容器启动，断言最终 `orders/stock/stock_logs/idempotency_records`，不能只看接口成功数。

## 8. 边界

A3 没有支付/取消/超时。不要通过手动删库存或改订单状态来“测试支付”；A4/A5 会引入独立状态转换接口与竞态测试。
