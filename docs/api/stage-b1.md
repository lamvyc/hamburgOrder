# 阶段 B1 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 可领取优惠券

```http
GET /api/coupons/available
```

权限：CUSTOMER。

```json
[
  {
    "id": 1,
    "code": "WELCOME5",
    "name": "新人立减 5 元",
    "discountAmount": "5.00",
    "minOrderAmount": "30.00",
    "remainingQuantity": 99,
    "claimed": true,
    "validFrom": "2026-01-01T00:00:00Z",
    "validTo": "2027-12-31T23:59:59Z"
  }
]
```

- 只返回 ACTIVE、当前在有效期内的模板；
- `claimed` 是当前用户是否已领；
- `remainingQuantity = total_quantity - claimed_quantity`。

## 2. 我的优惠券

```http
GET /api/coupons/mine
```

权限：CUSTOMER。

```json
[
  {
    "id": 1,
    "templateId": 1,
    "code": "WELCOME5",
    "name": "新人立减 5 元",
    "discountAmount": "5.00",
    "minOrderAmount": "30.00",
    "status": "UNUSED",
    "orderId": null,
    "claimedAt": "2026-11-09T12:46:18Z",
    "usedAt": null
  }
]
```

## 3. 领取

```http
POST /api/coupons/{templateId}/claim
X-XSRF-TOKEN: <csrf>
```

成功 201，返回 UNUSED 用户券。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 409 | `COUPON_ALREADY_CLAIMED` | 同一用户已领 |
| 409 | `COUPON_SOLD_OUT` | 总量已领完 |
| 409 | `COUPON_NOT_AVAILABLE` | 不存在/停用/不在有效期 |
| 403 | `FORBIDDEN` | 非 CUSTOMER/CSRF |
| 401 | `UNAUTHORIZED` | 未登录 |

## 4. 下单使用优惠券

`POST /api/orders` 增加可选字段：

```json
{
  "storeId": 1,
  "userCouponId": 1,
  "items": [{"productId": 1001, "quantity": 2}]
}
```

订单响应增加：

```json
{
  "totalAmount": "59.00",
  "discountAmount": "5.00"
}
```

规则：

- 券必须属于当前用户；
- 状态必须 UNUSED；
- 模板 ACTIVE 且在有效期；
- 商品小计 ≥ 门槛；
- 一张订单最多一张券；
- 券在下单事务内锁定；
- 幂等请求哈希包含 `userCouponId`，同 key 改券视为不同请求。

错误：

| HTTP | code |
| --- | --- |
| 404 | `COUPON_NOT_FOUND` |
| 409 | `COUPON_NOT_USABLE` |
| 409 | `COUPON_MIN_ORDER_NOT_MET` |
| 409 | `COUPON_STATE_INCONSISTENT` |

## 5. 支付/取消

- 支付：LOCKED → USED；
- 顾客取消：LOCKED → UNUSED；
- 超时取消：LOCKED → UNUSED；
- 已支付订单不可取消；
- 库存或优惠券任一步失败，订单事务整体回滚。

## 6. 未实现

- 百分比券；
- 多券叠加；
- 券码兑换；
- 管理端发券/作废；
- 券过期扫描。
