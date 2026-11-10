# 阶段 B3 架构：管理端商品游标分页

## 1. 目标

B3 在不改变 B2 管理写操作的前提下，增加：

1. 管理端商品 keyset 分页，不使用 OFFSET。
2. `afterId` 作为稳定游标。
3. 可选 `onSale` 筛选。
4. `limit` 限制 1–50。
5. 返回 `items / nextCursor / hasMore`。
6. 每页多取 1 条判断 hasMore，不额外 COUNT。
7. 前端“加载更多”，不一次性加载全部。
8. 通过受控数据验证索引与执行计划。

## 2. 为什么不用 OFFSET

```sql
LIMIT 5 OFFSET 1000
```

越往后数据库越要扫描并丢弃前 1000 行；期间新增/删除还会造成重复或漏项。

Keyset：

```sql
WHERE store_id=? AND id > ?
ORDER BY id
LIMIT ?
```

- 排序键 `id` 唯一；
- 下一页从上一页最后 id 之后开始；
- 复杂度不随页码增长；
- 但只能顺序翻页，不能随机跳页。

## 3. 分页协议

请求：

```text
GET /api/admin/stores/1/products/page?limit=5
GET /api/admin/stores/1/products/page?limit=5&afterId=1005
GET /api/admin/stores/1/products/page?limit=5&onSale=true
```

响应：

```json
{
  "items": [],
  "nextCursor": 1005,
  "hasMore": true
}
```

- 查询取 `limit+1`；
- 实际返回前 limit 条；
- 多出的那条只用于 `hasMore`；
- `nextCursor` 是本页最后一条 id；
- `hasMore=false` 时 `nextCursor=null`。

## 4. 索引与优化器

V12：

```sql
CREATE INDEX idx_products_store_id ON products (store_id, id);
CREATE INDEX idx_products_store_onsale_id ON products (store_id, on_sale, id);
```

受控 100 行实验发现：不强制索引时，MySQL 仍选择 `uk_products_store_code` + filesort。因此 Mapper 根据是否筛选上架显式：

```text
onSale != null -> FORCE INDEX (idx_products_store_onsale_id)
否则           -> FORCE INDEX (idx_products_store_id)
```

这是有意的权衡：

- 保证 keyset 排序使用索引；
- 代价是索引改名/删除会导致 SQL 失败；
- 若未来查询列/过滤改变，需要重新 EXPLAIN；
- 小数据集上优化器可能仍认为 filesort 更便宜，FORCE 是显式约束。

不把“加了索引”当成“索引一定被使用”。

## 5. 前端

`/admin/catalog`：

- 首屏 5 条；
- “加载更多”使用 nextCursor；
- “只看上架”触发第一页重新查询；
- 商品/库存编辑后刷新第一页；
- 审计照常加载。

## 6. 边界

未实现：

- 搜索关键词；
- 随机跳页/总数；
- 管理审计日志分页；
- 百万级数据压测；
- 并发删除/更新下的游标稳定性；
- 多字段排序游标。
