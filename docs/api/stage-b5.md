# 阶段 B5 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 批量预览

```http
POST /api/admin/stores/{storeId}/stock-adjustments/batch/preview
X-XSRF-TOKEN: <csrf>

{
  "lines": [
    {"productId": 1001, "delta": 1, "reason": "合法"},
    {"productId": 1002, "delta": 0, "reason": "零"},
    {"productId": 1006, "delta": -1000, "reason": "不足"}
  ]
}
```

权限：ADMIN。

成功 200：

```json
{
  "valid": false,
  "errorCount": 2,
  "lines": [
    {
      "lineNo": 1,
      "productId": 1001,
      "delta": 1,
      "reason": "合法",
      "valid": true,
      "code": null,
      "message": null,
      "availableBefore": 44,
      "availableAfter": 45
    },
    {
      "lineNo": 2,
      "productId": 1002,
      "delta": 0,
      "reason": "零",
      "valid": false,
      "code": "INVALID_STOCK_DELTA",
      "message": "调整量不能为 0",
      "availableBefore": 30,
      "availableAfter": null
    }
  ]
}
```

不写库存/批次/流水/审计。

错误码：

- `DUPLICATE_BATCH_PRODUCT`
- `INVALID_STOCK_DELTA`
- `PRODUCT_NOT_FOUND`
- `STOCK_INSUFFICIENT`

## 2. 正式执行

仍使用：

```text
POST /api/admin/stores/{storeId}/stock-adjustments/batch
Idempotency-Key: ...
```

预览与执行之间库存可能变化，执行会重新校验。

## 3. 缺少的字段

预览不返回批次 ID、不产生审计；这些属于执行。

## 4. 未实现

- CSV 文件；
- dry-run 与执行 diff；
- 部分成功；
- 预览持久化。
