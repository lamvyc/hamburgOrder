# 阶段 B3 排错入口

学习故障练习在 [阶段 B3 学习材料](../learning/stage-b3.md)。

## 1. 第二页重复/漏项

检查：

- 是否仍用 OFFSET；
- `afterId` 是否为本页最后一条 id；
- 排序是否 `ORDER BY id` 且唯一；
- 两页是否使用相同 `onSale`/store 条件；
- 前端是否把第二页 append 到旧数组而不是覆盖。

```sql
SELECT id, product_code FROM products
WHERE store_id=? AND id > ?
ORDER BY id LIMIT ?;
```

## 2. hasMore 不对

实现取 `limit+1`：

```text
rows.size() > limit -> hasMore=true
nextCursor = 第 limit 条 id
否则 nextCursor=null
```

如果先 COUNT 再查，可能在两步之间变化；B3 不用 COUNT。

## 3. EXPLAIN 仍然 filesort

- 是否真的选了新索引：`key=...`；
- 是否 `ORDER BY` 与索引列顺序一致；
- 是否 `onSale` 条件与索引顺序匹配；
- 小数据下优化器可能认为唯一键更便宜；
- B3 显式 FORCE INDEX，属于有意取舍。

```sql
EXPLAIN SELECT ...
FROM products p FORCE INDEX (idx_products_store_id)
JOIN stock s ...
WHERE p.store_id=? AND p.id > ?
ORDER BY p.id LIMIT ?;
```

应看到 `type=range`、`key=idx_products_store_id`、无 filesort。

## 4. FORCE INDEX 的风险

- 索引改名/删除会直接报错；
- 数据/查询变化后可能不再是最好计划；
- 需要迁移/EXPLAIN 回归；
- 不能把它当成永久免死金牌。

## 5. 前端“加载更多”按钮不消失

- 后端 `hasMore` 是否 false；
- `nextCursor` 是否为 null；
- 前端 append 后是否更新 hasMore；
- 是否切换筛选后没有重置 cursor。

## 6. 状态标签截图变成细条

Element Plus `el-tag` 挂载有短动画，截图时可能 `scaleX≈0`。E2E 等标签 `getBoundingClientRect().width > 20` 再截图。

## 7. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:b3
```
