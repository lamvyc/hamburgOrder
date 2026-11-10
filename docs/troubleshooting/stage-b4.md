# 阶段 B4 排错入口

学习故障练习在 [阶段 B4 学习材料](../learning/stage-b4.md)。

## 1. 批次 409

| code | 先查 |
| --- | --- |
| `IDEMPOTENCY_CONFLICT` | 同 key 的 request_hash 是否不同 |
| `BATCH_IN_PROGRESS` | 是否有并发事务未完成；是否误用普通读 |
| `STOCK_INSUFFICIENT` | 某行 available+delta<0 |
| `PRODUCT_NOT_FOUND` | 商品是否属于该 store |
| `DUPLICATE_BATCH_PRODUCT` | 同批是否重复 productId |

```sql
SELECT id, admin_user_id, idempotency_key, request_hash, status, line_count
FROM admin_stock_batches WHERE admin_user_id=? ORDER BY id DESC;

SELECT batch_id, product_id, delta, available_before, available_after
FROM admin_stock_batch_lines WHERE batch_id=?;
```

## 2. 库存被应用两次

- 是否两个 batch id？
- 两个 batch 的 idempotency_key 是否不同？
- 是否同一管理员？
- request_hash 是否相同？
- 前端是否每次重试生成新 key？
- 唯一键是否真的存在？

不要用“删掉一条流水”修复；先定位幂等边界。

## 3. 同键并发报“处理中”

B4 实际踩过：唯一键冲突后普通 SELECT 在 REPEATABLE READ 快照下看不到已提交 batch。修复：

```text
SELECT ... WHERE admin_user_id=? AND idempotency_key=? FOR UPDATE
```

锁定读是当前读，能看到等待期间提交的行。

## 4. 批次回滚不彻底

检查是否：

- 每行更新和 batch 插入在同一 `@Transactional`；
- 是否调用了另一个 REQUIRES_NEW 服务；
- 是否先提交库存再写批次；
- 是否捕获异常后没有重新抛出，导致事务提交；
- audit/log 是否在同一事务。

预期失败批次：

```text
batch rows = 0
batch lines = 0
stock unchanged
stock_logs = 0
admin_audit_logs = 0
```

## 5. 审计只有一行但有 N 行库存

这是设计：

- 每行 ADJUST 库存流水；
- 整个批次一条 STOCK_BATCH 审计，detail 包含 product 列表。

## 6. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:b4
```
