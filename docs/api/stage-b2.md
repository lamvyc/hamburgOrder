# 阶段 B2 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 管理员商品列表

```http
GET /api/admin/stores/{storeId}/products
```

权限：ADMIN。

返回该门店全部商品（包含下架）与库存：

```json
[
  {
    "id": 1001,
    "storeId": 1,
    "code": "S1-CLASSIC",
    "name": "经典牛肉堡",
    "categoryName": "汉堡",
    "price": "32.00",
    "onSale": true,
    "version": 0,
    "available": 40,
    "reserved": 4,
    "stockVersion": 52
  }
]
```

## 2. 改价/上下架

```http
PATCH /api/admin/products/{productId}
X-XSRF-TOKEN: <csrf>

{
  "price": "33.50",
  "onSale": false,
  "version": 0
}
```

成功 200 返回更新后的商品行，version+1。

错误：

| HTTP | code |
| --- | --- |
| 400 | `INVALID_REQUEST` |
| 404 | `PRODUCT_NOT_FOUND` |
| 409 | `VERSION_CONFLICT` |
| 403 | `FORBIDDEN` |

公开菜单会立即反映价格/上下架：下架商品不再出现在菜单。

## 3. 调整库存

```http
POST /api/admin/products/{productId}/stock-adjustments
X-XSRF-TOKEN: <csrf>

{
  "delta": 5,
  "version": 41,
  "reason": "到货补货"
}
```

成功 200：

```json
{
  "productId": 1001,
  "available": 48,
  "reserved": 2,
  "version": 42
}
```

规则：

- delta 非 0；
- 只改 available，不改 reserved；
- `available + delta >= 0`；
- 版本必须匹配；
- 写 ADJUST stock log（order_id NULL、operator）；
- 写审计。

错误：

- 400 `INVALID_REQUEST` / `INVALID_STOCK_DELTA`；
- 404 `PRODUCT_NOT_FOUND`；
- 409 `VERSION_CONFLICT`；
- 409 `STOCK_INSUFFICIENT`。

## 4. 审计日志

```http
GET /api/admin/audit-logs?limit=20
```

权限：ADMIN；limit 被限制在 1–100。

```json
[
  {
    "id": 3,
    "actorName": "演示管理员",
    "action": "STOCK_ADJUST",
    "targetType": "PRODUCT",
    "targetId": 1001,
    "detail": "{\"delta\":1,\"reason\":\"到货\"}",
    "createdAt": "2026-11-09T13:00:00Z"
  }
]
```

## 5. 权限与错误

- 顾客/员工：403；
- 未登录：401；
- 旧版本：409；
- 库存为负：409；
- 管理接口都要求 CSRF（POST/PATCH）。

## 6. 未实现

- 商品新建/删除；
- 门店营业状态修改；
- 批量导入；
- 管理列表游标分页；
- 审计导出/归档。
