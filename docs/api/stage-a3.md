# 阶段 A3 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 下单

```http
POST /api/orders
Content-Type: application/json
X-XSRF-TOKEN: <csrf>
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "storeId": 1,
  "items": [
    {"productId": 1001, "quantity": 2},
    {"productId": 1006, "quantity": 1}
  ]
}
```

要求：

- ROLE_CUSTOMER + Session + CSRF；
- `Idempotency-Key` 8–64 位 `[A-Za-z0-9._-]`；
- items 1–20 种，quantity 1–99；
- 同一请求中 productId 不允许重复；
- 不接收客户端价格。

成功首次：HTTP 201

```json
{
  "id": 1,
  "orderNo": "BO202611091157037CC724",
  "status": "WAIT_PAY",
  "storeId": 1,
  "storeName": "中央车站店",
  "totalAmount": "74.00",
  "currency": "CNY",
  "expiresAt": "2026-11-09T12:27:03.878265Z",
  "createdAt": "2026-11-09T11:57:03.878265Z",
  "items": [
    {
      "productId": 1001,
      "productCode": "S1-CLASSIC",
      "productName": "经典牛肉堡",
      "unitPrice": "32.00",
      "quantity": 2,
      "lineAmount": "64.00"
    }
  ]
}
```

响应头：

```text
Location: /api/orders/1
Idempotent-Replayed: false
```

同一个 key + 同一请求体重放：HTTP 200，相同订单，`Idempotent-Replayed: true`。

## 2. 幂等冲突

同一个 key、不同请求体：HTTP 409

```json
{
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "相同幂等键的请求内容不一致"
}
```

同一 key 正在处理：409 `IDEMPOTENCY_IN_PROGRESS`。

A3 的并发实现依赖数据库唯一约束：

```text
UNIQUE (user_id, operation, idempotency_key)
```

没有前端按钮锁或单 JVM 内存锁充当正确性保证。

## 3. 库存与门店冲突

| HTTP | code | 场景 |
| --- | --- | --- |
| 409 | `STOCK_INSUFFICIENT` | 条件更新影响行数为 0 |
| 409 | `STORE_NOT_ORDERABLE` | 门店休息或暂停接单 |
| 409 | `PRODUCT_UNAVAILABLE` | 商品不存在、下架或不属于该门店 |
| 400 | `DUPLICATE_ORDER_ITEM` | 请求内重复商品 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 未传幂等键 |
| 400 | `INVALID_IDEMPOTENCY_KEY` | 幂等键格式错误 |

库存不足时整个事务回滚：

- 不留下订单、明细、库存流水、事件或幂等记录；
- 前面已预占的商品也回滚。

## 4. 读取订单

```http
GET /api/orders/{orderId}
```

仅 ROLE_CUSTOMER，且订单 `user_id` 必须等于当前 principal。

不存在或不属于当前用户：HTTP 404

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "订单不存在"
}
```

这里统一 404 而不是 403，避免向其他顾客暴露订单是否存在。

## 5. 错误与事务

- 409 表示可识别的业务冲突，事务回滚；
- 400 表示请求格式/内容不合法；
- 401 未登录；
- 403 角色不足或 CSRF 失败；
- 500 未预期异常；
- 503 数据库连接不可用。

所有错误带 traceId；不返回 SQL、堆栈或内部 ID 细节。

## 6. 没有支付

A3 订单固定 `WAIT_PAY`。

没有：

- 支付接口；
- 取消接口；
- 超时释放任务；
- 员工订单工作台。

前端不显示假的支付按钮，只显示 A4 提示。

## 7. 前端幂等键

`frontend/src/stores/checkout.ts`：

```text
localStorage: burger-order.checkout.v1
fingerprint: storeId + sorted(productId:quantity)
```

- 同购物车重试复用 key；
- 购物车变化生成新 key；
- 成功清空 key 与购物车；
- 网络失败保留 key，避免“请求已成功但响应丢失”后重复下单。

## 8. 并发证据

- 同 key 双请求：1 个订单，1 次库存预占，1 条幂等记录；
- 最后 1 件库存、不同 key：1 成功 + 1 `STOCK_INSUFFICIENT`，最终 `available=0,reserved=1`；
- 多商品第二项库存不足：第一项预占也回滚。

完整证据：[A3 证据](../evidence/stage-a3.md)。
