# 阶段 B4 数据库说明

## 1. V13 迁移

```text
V13__create_admin_stock_batches.sql
```

新增：

- `admin_stock_batches`
- `admin_stock_batch_lines`

## 2. admin_stock_batches

| 列 | 说明 |
| --- | --- |
| `admin_user_id` | 操作管理员 |
| `store_id` | 门店 |
| `idempotency_key` | 幂等键 |
| `request_hash` | 规范化请求 SHA-256 |
| `status` | PROCESSING / COMPLETED |
| `line_count` | 行数 |
| `created_at` | UTC |

约束：

```text
UNIQUE (admin_user_id, idempotency_key)
CHECK line_count > 0
CHECK status IN ('PROCESSING','COMPLETED')
FK admin_user_id -> users
FK store_id -> stores
```

## 3. admin_stock_batch_lines

| 列 | 说明 |
| --- | --- |
| `batch_id` / `product_id` | 批次与商品 |
| `delta` | 调整量 |
| `reason` | 行原因 |
| `available_before/after` | 库存前后值 |
| `stock_version_after` | 库存版本 |

约束：

```text
UNIQUE (batch_id, product_id)
CHECK delta <> 0
CHECK available_before >= 0
CHECK available_after >= 0
FK batch/product
```

## 4. 实际批次

```text
batch 1 COMPLETED 2 lines hash a5f76b996c66...
  1001 +1 42 -> 43
  1002 +1 30 -> 31

batch 2 COMPLETED 2 lines hash b68c5f34ccac...
  1001 -1 43 -> 42
  1002 -1 31 -> 30
```

库存流水：

```text
ADJUST order_id=NULL operator=3
reason=BATCH#1 E2E批次补货
reason=BATCH#2 E2E批次恢复
```

审计：

```json
{"batchId":1,"appliedCount":2,"products":[1001,1002]}
```

## 5. 事务/锁

- 批次插入与全部行更新同一事务；
- 行按 productId 排序，固定锁顺序；
- 单行 `available+delta>=0` 条件；
- 任一行失败 → 批次、行、库存、流水、审计全部回滚；
- 同键并发 duplicate 后使用 `SELECT ... FOR UPDATE` 读取已提交批次；
- REPEATABLE READ 普通读可能看到旧快照，B4 测试实际踩到。

## 6. 验证

```sql
SELECT id, admin_user_id, status, line_count, LEFT(request_hash,12)
FROM admin_stock_batches ORDER BY id;

SELECT batch_id, product_id, delta, available_before, available_after
FROM admin_stock_batch_lines ORDER BY batch_id, product_id;

SELECT order_id, operator_user_id, change_type, available_delta, reason
FROM stock_logs WHERE reason LIKE 'BATCH#%' ORDER BY id;
```

## 7. 未建立

- 部分成功状态；
- dry-run/预览；
- 批次文件/对象存储；
- 异步任务/进度；
- 批次历史分页；
- 撤销批次。
