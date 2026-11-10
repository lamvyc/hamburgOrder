# 阶段 B5 数据库说明

## 1. 迁移

B5 没有新增迁移。数据库仍为 V13。

## 2. 只读校验

预览查询：

```sql
SELECT p.id, p.store_id, p.product_code, p.version,
       s.available, s.reserved, s.version
FROM products p
JOIN stock s ON s.product_id = p.id AND s.store_id = p.store_id
WHERE p.id = ?;
```

不执行：

- INSERT admin_stock_batches；
- INSERT admin_stock_batch_lines；
- UPDATE stock；
- INSERT stock_logs；
- INSERT admin_audit_logs。

## 3. 预计值

```text
availableBefore = stock.available
availableAfter  = stock.available + delta
```

只做计算，不持久化。

## 4. 验证

预览前后：

```sql
SELECT available, reserved, version FROM stock WHERE product_id=1001;
SELECT COUNT(*) FROM admin_stock_batches;
SELECT COUNT(*) FROM admin_stock_batch_lines;
SELECT COUNT(*) FROM stock_logs;
SELECT COUNT(*) FROM admin_audit_logs;
```

应全部保持不变。

## 5. 边界

- 预览不锁行，不保证执行时库存仍满足；
- 正式执行由 B4 条件更新重新校验；
- 预览不产生幂等记录；
- 未实现 CSV/文件。
