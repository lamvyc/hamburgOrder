# 阶段 B3 数据库说明

## 1. V12 迁移

```text
V12__add_admin_product_pagination_indexes.sql
```

```sql
CREATE INDEX idx_products_store_id
    ON products (store_id, id);

CREATE INDEX idx_products_store_onsale_id
    ON products (store_id, on_sale, id);
```

没有新增表。旧 `GET /products` 仍保留；新页面接口使用 `/products/page`。

## 2. Keyset SQL

无筛选：

```sql
SELECT ...
FROM products p FORCE INDEX (idx_products_store_id)
JOIN stock s ON ...
WHERE p.store_id=? AND p.id > ?
ORDER BY p.id
LIMIT ?;
```

上架筛选：

```sql
FROM products p FORCE INDEX (idx_products_store_onsale_id)
WHERE p.store_id=? AND p.on_sale=?
  AND p.id > ?
ORDER BY p.id
LIMIT ?;
```

取 `limit+1` 用于 `hasMore`。

## 3. 受控执行计划

在事务内插入 100 行临时商品，执行 EXPLAIN 后 ROLLBACK。

不强制索引：

```text
key=uk_products_store_code
rows=107
Extra=Using index condition; Using filesort
```

强制 keyset 索引（无筛选）：

```text
type=range
key=idx_products_store_id
rows=107
Extra=Using index condition
```

上架筛选：

```text
type=range
key=idx_products_store_onsale_id
key_len=17
Extra=Using index condition
```

两者都没有 filesort。

## 4. 小数据集现实

开发库只有 7 个商品；不加 FORCE 时优化器会选择唯一键 + filesort。B3 因此显式 FORCE INDEX，并在文档中记录维护成本。

## 5. 游标

`afterId` 是上一页最后一条商品 id，不是页码。

- 排序键唯一，避免同 id 重复；
- 新增商品 id 更大，会出现在后续页；
- 删除游标之前的行不会造成偏移；
- 删除游标之后的行可能使下一页少一条，但不会重复；
- 随机跳页未实现。

## 6. 验证

```sql
EXPLAIN SELECT ...
FROM products p FORCE INDEX (idx_products_store_id)
JOIN stock s ON s.product_id=p.id AND s.store_id=p.store_id
WHERE p.store_id=1 AND p.id > 0
ORDER BY p.id LIMIT 6;
```

## 7. 未建立

- 关键词搜索索引；
- 管理审计分页索引；
- 多列排序游标；
- 总数缓存/近似计数；
- 百万行压测。
