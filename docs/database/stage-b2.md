# 阶段 B2 数据库说明

## 1. V11 迁移

```text
V11__add_admin_inventory_management.sql
```

内容：

1. `stock_logs.order_id` 改为可空；
2. `stock_logs.operator_user_id` 新增；
3. `products.version` 新增；
4. `admin_audit_logs` 新增。

## 2. products.version

```sql
version INT NOT NULL DEFAULT 0
```

- 管理员改价/上下架时条件更新并 +1；
- 公开菜单不展示版本；
- 管理列表同时展示商品版本与库存版本。

## 3. stock_logs

```text
order_id          BIGINT NULL
operator_user_id  BIGINT NULL FK users
```

约束保持：

- `change_type` RESERVE/COMMIT/RELEASE/ADJUST；
- available/reserved delta 与 after 值；
- order_id 非空时 FK orders。

管理员调整：

```text
order_id=NULL, operator_user_id=admin, change_type=ADJUST
```

实际示例：

```text
NULL 3 ADJUST +1 0 after=41 reserved=4 reason=E2E 补货
NULL 3 ADJUST -1 0 after=40 reserved=4 reason=E2E 恢复
```

## 4. admin_audit_logs

| 列 | 说明 |
| --- | --- |
| `actor_user_id` | 管理员 |
| `action` | PRODUCT_UPDATE / STOCK_ADJUST |
| `target_type` / `target_id` | PRODUCT |
| `detail` | JSON 前后值/原因 |
| `created_at` | UTC |

JSON 由 Jackson 序列化，MySQL JSON 校验。

## 5. 实际数据

商品 1001：

```text
price=32.00 on_sale=1 version=0
```

库存：

```text
available=40 reserved=4 version=52
```

审计：

```json
{"delta": -1, "reason": "E2E 恢复", "afterAvailable": 40, "beforeAvailable": 41}
```

## 6. 管理员商品查询计划

```sql
EXPLAIN SELECT ...
FROM products p JOIN stock s
  ON s.product_id=p.id AND s.store_id=p.store_id
WHERE p.store_id=1
ORDER BY p.category_sort, p.sort_order, p.id;
```

实际：

```text
products: ref uk_products_store_code, rows=7, Using filesort
stock:    eq_ref PRIMARY, rows=1
```

没有为“管理员含下架商品按分类排序”新增索引；当前 7 行可接受，B3 再评估。

## 7. 验证

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id,product_code,price,on_sale,version FROM products WHERE id=1001;
      SELECT product_id,available,reserved,version FROM stock WHERE product_id=1001;
      SELECT order_id,operator_user_id,change_type,available_delta,reserved_delta,reason
        FROM stock_logs WHERE change_type='ADJUST' ORDER BY id DESC LIMIT 5;
      SELECT actor_user_id,action,target_id,detail FROM admin_audit_logs ORDER BY id DESC LIMIT 5;"
```

## 8. 未建立

- 商品创建/删除版本；
- 门店配置版本；
- 批量操作表；
- 审计归档/导出；
- 管理列表专用索引；
- 审批/回滚表。
