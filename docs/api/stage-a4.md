# 阶段 A4 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 模拟支付

```http
POST /api/orders/{orderId}/pay
X-XSRF-TOKEN: <csrf>
```

权限：ROLE_CUSTOMER，且订单属于当前用户。

成功 200：

```json
{
  "id": 7,
  "orderNo": "BO20261109121648F1342C",
  "status": "WAIT_MAKE",
  "storeId": 1,
  "storeName": "中央车站店",
  "totalAmount": "32.00",
  "currency": "CNY",
  "expiresAt": "2026-11-09T12:46:48Z",
  "createdAt": "2026-11-09T12:16:48Z",
  "items": [
    {
      "productId": 1001,
      "productCode": "S1-CLASSIC",
      "productName": "经典牛肉堡",
      "unitPrice": "32.00",
      "quantity": 1,
      "lineAmount": "32.00"
    }
  ]
}
```

副作用：

- 订单 `WAIT_PAY → WAIT_MAKE`；
- 每条 order_item 对应 `stock.reserved -= qty`，`available` 不变；
- 写 stock_logs `COMMIT`；
- 写 order_events `PAYMENT_SUCCEEDED`。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | `ORDER_NOT_FOUND` | 订单不存在或不属于当前顾客 |
| 409 | `ORDER_EXPIRED` | expires_at 已过 |
| 409 | `ORDER_STATUS_CONFLICT` | 已支付或非 WAIT_PAY |
| 403 | `FORBIDDEN` | 员工/管理员调用 |
| 401 | `UNAUTHORIZED` | 未登录 |

支付没有独立 payments 表；A4 是模拟支付。

## 2. 订单事件轨迹

```http
GET /api/orders/{orderId}/events
```

权限：订单所属 CUSTOMER。

成功 200：

```json
[
  {
    "id": 1,
    "eventType": "CREATED",
    "fromStatus": null,
    "toStatus": "WAIT_PAY",
    "operatorName": "演示顾客",
    "reason": "顾客创建订单",
    "createdAt": "2026-11-09T12:16:48Z"
  },
  {
    "id": 2,
    "eventType": "PAYMENT_SUCCEEDED",
    "fromStatus": "WAIT_PAY",
    "toStatus": "WAIT_MAKE",
    "operatorName": "演示顾客",
    "reason": "模拟支付成功",
    "createdAt": "2026-11-09T12:16:49Z"
  }
]
```

订单 7 完整轨迹：

```text
CREATED
PAYMENT_SUCCEEDED
MAKING_STARTED
READY_FOR_PICKUP
COMPLETED
```

不存在或不属于自己：404 `ORDER_NOT_FOUND`。

## 3. 员工活跃订单

```http
GET /api/staff/orders?storeId=1
```

权限：ROLE_STAFF，且当前员工属于 storeId。

成功 200：

```json
{
  "items": [
    {
      "id": 7,
      "orderNo": "BO20261109121648F1342C",
      "status": "WAIT_MAKE",
      "storeId": 1,
      "storeName": "中央车站店",
      "totalAmount": "32.00",
      "currency": "CNY",
      "expiresAt": "2026-11-09T12:46:48Z",
      "createdAt": "2026-11-09T12:16:48Z",
      "items": [
        {
          "productId": 1001,
          "productCode": "S1-CLASSIC",
          "productName": "经典牛肉堡",
          "unitPrice": "32.00",
          "quantity": 1,
          "lineAmount": "32.00"
        }
      ]
    }
  ]
}
```

只返回：

```text
WAIT_MAKE, MAKING, READY
```

按 created_at、id 升序。已支付订单进入该列表；COMPLETED/CANCELLED 不再显示。

错误：

- 缺少 storeId：400 `INVALID_PARAMETER`；
- 员工不属于该门店：404 `STORE_NOT_FOUND`；
- 顾客/管理员访问：403；
- 未登录：401。

## 4. 员工状态转换

```text
POST /api/staff/orders/{orderId}/start      WAIT_MAKE -> MAKING
POST /api/staff/orders/{orderId}/ready      MAKING    -> READY
POST /api/staff/orders/{orderId}/complete   READY     -> COMPLETED
```

权限：ROLE_STAFF + 所属门店（订单 store_id）。

成功 200 返回更新后的 OrderResponse。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | `ORDER_NOT_FOUND` | 不存在或不属于员工门店 |
| 409 | `ORDER_STATUS_CONFLICT` | 前置状态不合法/已被其他操作改变 |
| 403 | `FORBIDDEN` | 角色不足或 CSRF |
| 401 | `UNAUTHORIZED` | 未登录 |

事件类型：

```text
MAKING_STARTED
READY_FOR_PICKUP
COMPLETED
```

## 5. 并发与重复操作

- 订单状态更新是条件 UPDATE；影响行数必须为 1。
- 同订单并发支付：一个 200，一个 409；库存只 COMMIT 一次。
- 重复支付：409。
- 重复开始制作：已进入 MAKING 后再 start → 409。
- 员工操作其他门店订单：404，不泄露订单存在性。

## 6. 未实现

- 顾客取消；
- 超时扫描；
- 支付/取消竞态；
- payments 回调；
- 退款/对账；
- 员工订单历史与统计。
