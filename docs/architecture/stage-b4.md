# 阶段 B4 架构：幂等批量库存调整

## 1. 目标

B4 增加管理员批量库存调整：

1. 一个批次包含 1–100 行 `productId/delta/reason`。
2. 所有行在同一事务内，全有或全无。
3. 任一行失败，整批回滚，不留下库存、流水、批次或审计半状态。
4. `Idempotency-Key` 按管理员隔离；同键同内容重放，同键不同内容 409。
5. 每行写 ADJUST 流水；整批写一条审计。
6. 并发同键只应用一次。
7. 管理台批量文本框提交并刷新库存。

## 2. 数据模型

```text
admin_stock_batches
  1 ─── n admin_stock_batch_lines
```

- batch：admin、store、idempotency_key、request_hash、status、line_count。
- line：product、delta、reason、available_before/after、stock_version_after。
- 唯一键：`(admin_user_id, idempotency_key)`。
- 唯一键：`(batch_id, product_id)`，同批不能重复商品。

## 3. 请求哈希

规范化：

```text
storeId=1|lines=1001:1:补货,1002:-1:盘点
```

- 按 productId 排序；
- 包含 delta 与 reason；
- SHA-256；
- 同 key + 同 hash → 重放；
- 同 key + 不同 hash → 409 `IDEMPOTENCY_CONFLICT`。

## 4. 事务流程

```text
校验 key/行数/重复商品/delta
计算 hash
查已有 batch
  - 同 hash COMPLETED → replay
  - 不同 hash → 409
  - PROCESSING → 409
INSERT batch PROCESSING（唯一键闸门）
for line sorted by productId:
  校验商品属于门店
  UPDATE stock SET available=available+delta, version=version+1
   WHERE store_id=? AND product_id=? AND available+delta>=0
  affected=1
  写 stock_logs ADJUST（order_id NULL, operator）
  写 batch line 前后值
UPDATE batch COMPLETED
写一条 admin_audit STOCK_BATCH
COMMIT
```

任何异常 → 整批回滚。

## 5. 并发幂等

同键并发：

- 两个事务都先查不到 batch；
- 一个 INSERT 成功；
- 另一个 INSERT 在唯一键上等待；
- 第一个 COMMIT 后，第二个 duplicate；
- 第二个用 `SELECT ... FOR UPDATE` 读取已提交 batch；
- 同 hash 返回重放，不再执行库存更新。

### REPEATABLE READ 的坑

普通 `SELECT` 在 MySQL REPEATABLE READ 下可能继续使用旧快照，看不到等待期间提交的 batch，从而误报 `BATCH_IN_PROGRESS`。因此 duplicate 后的重读必须是 `FOR UPDATE`（当前读）。

B4 实际测试先失败后修复，证据保留。

## 6. 流水与审计

每行：

```text
stock_logs ADJUST order_id=NULL operator=admin reason=BATCH#id 原因
```

整批：

```text
admin_audit_logs action=STOCK_BATCH target=STORE
detail={batchId, appliedCount, products}
```

## 7. 前端

管理台“批量调整”对话框：

- 每行 `productId,delta,reason`；
- 文本解析后一次性提交；
- 成功后刷新第一页与审计；
- 失败显示错误码/traceId；
- 每批生成一个幂等键。

## 8. 边界

未实现：

- 部分成功/逐行结果；
- CSV 文件上传；
- dry-run；
- 异步批次/进度；
- 超过 100 行的批次；
- 批次历史查询；
- 批次撤销/重放操作。
