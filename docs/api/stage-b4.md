# 阶段 B4 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 批量库存调整

```http
POST /api/admin/stores/{storeId}/stock-adjustments/batch
Idempotency-Key: batch-key-0001
X-XSRF-TOKEN: <csrf>

{
  "lines": [
    {"productId": 1001, "delta": 1, "reason": "到货补货"},
    {"productId": 1002, "delta": -1, "reason": "盘点损耗"}
  ]
}
```

权限：ADMIN + CSRF。

成功 201：

```json
{
  "batchId": 1,
  "appliedCount": 2,
  "lines": [
    {
      "productId": 1001,
      "delta": 1,
      "reason": "到货补货",
      "availableBefore": 42,
      "availableAfter": 43,
      "stockVersionAfter": 58
    }
  ]
}
```

同 key + 同内容重放：HTTP 200，`Idempotent-Replayed: true`，同一 batchId，库存不再变化。

同 key + 不同内容：409 `IDEMPOTENCY_CONFLICT`。

## 2. 规则

- 1–100 行；
- delta 非 0；
- 同一批 productId 不能重复；
- product 必须属于 store；
- `available + delta >= 0`；
- 全有或全无；
- 每行写 ADJUST 流水；
- 整批写一条 STOCK_BATCH 审计。

## 3. 错误

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 缺 key |
| 400 | `INVALID_IDEMPOTENCY_KEY` | key 格式错误 |
| 400 | `INVALID_BATCH` | 空批/超 100 行 |
| 400 | `INVALID_STOCK_DELTA` | delta=0 |
| 400 | `DUPLICATE_BATCH_PRODUCT` | 同批重复商品 |
| 404 | `PRODUCT_NOT_FOUND` | 商品/门店不匹配 |
| 409 | `STOCK_INSUFFICIENT` | 库存不足 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同键不同内容 |
| 409 | `BATCH_IN_PROGRESS` | 同键正在处理 |
| 403 | `FORBIDDEN` | 非 ADMIN/CSRF |

## 4. 并发

同键并发只有一个批次应用库存；另一个等待唯一键后重放，使用 `FOR UPDATE` 读取已提交批次。

## 5. 未实现

- 部分成功；
- 逐行错误汇总；
- CSV 上传；
- dry-run；
- 批次历史/撤销。
