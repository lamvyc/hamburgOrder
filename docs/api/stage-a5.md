# 阶段 A5 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 顾客取消未支付订单

```http
POST /api/orders/{orderId}/cancel
X-XSRF-TOKEN: <csrf>
```

权限：ROLE_CUSTOMER，且订单属于当前用户。

成功 200，返回更新后的 OrderResponse，status=CANCELLED。

副作用：

- 订单 `WAIT_PAY → CANCELLED`；
- 每条明细：`stock.available += qty`、`stock.reserved -= qty`；
- stock_logs `RELEASE`；
- order_events `CANCELLED`；
- 以上同一事务。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | `ORDER_NOT_FOUND` | 不存在或不属于当前顾客 |
| 409 | `ORDER_STATUS_CONFLICT` | 已支付/已完成/已取消 |
| 403 | `FORBIDDEN` | 员工/管理员或 CSRF |
| 401 | `UNAUTHORIZED` | 未登录 |
| 503 | `DATABASE_UNAVAILABLE` | 数据库不可用 |

再次取消：409，不重复释放库存。

## 2. 超时取消

没有公开 HTTP 端点。

后台任务按 `(status, expires_at, id)` 索引扫描：

```sql
SELECT id FROM orders
WHERE status='WAIT_PAY' AND expires_at <= now
ORDER BY expires_at, id
LIMIT 100;
```

每个订单：

- 条件更新为 CANCELLED；
- 释放库存；
- 写 RELEASE 与 CANCELLED 事件；
- operator_user_id=NULL；
- reason=支付超时自动取消。

调度：

```yaml
app.order.timeout-scan:
  enabled: true
  initial-delay-ms: 30000
  fixed-delay-ms: 60000
  batch-size: 100
```

## 3. 支付/取消竞争

只允许：

- 支付成功：WAIT_MAKE，COMMIT；
- 或取消成功：CANCELLED，RELEASE。

失败一方收到 409。不会同时出现 COMMIT 与 RELEASE。

## 4. 事件轨迹

顾客可通过：

```text
GET /api/orders/{orderId}/events
```

看到：

```json
{
  "eventType": "CANCELLED",
  "fromStatus": "WAIT_PAY",
  "toStatus": "CANCELLED",
  "operatorName": "演示顾客",
  "reason": "顾客取消订单"
}
```

超时取消的 `operatorName` 为 null，reason 为“支付超时自动取消”。

## 5. 边界

- 已支付订单不能取消；
- 无退款；
- 无优惠券释放；
- 无取消原因码；
- 不提供管理员强制取消；
- 超时扫描任务不提供公开触发端点。
