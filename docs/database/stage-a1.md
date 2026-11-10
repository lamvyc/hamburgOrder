# 阶段 A1 数据库说明

## 1. 迁移

唯一来源：

```text
backend/src/main/resources/db/migration/
  V1__create_system_metadata.sql
  V2__create_store_catalog.sql
  V3__seed_stage_a1_catalog.sql
```

- V1 保留不动。
- V2 创建 `stores`、`products`。
- V3 插入 3 家门店、18 个商品。
- 不修改已经执行过的迁移。A2 起新增 V4、V5。

## 2. stores

关键列：

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `code` | VARCHAR(32) | 唯一门店编码 |
| `name` | VARCHAR(80) | 非空 |
| `address` | VARCHAR(255) | 非空 |
| `latitude` / `longitude` | DECIMAL(9,6) | CHECK 范围校验 |
| `business_status` | VARCHAR(16) | CHECK 只允许 `OPEN` / `CLOSED` |
| `accepting_orders` | TINYINT | CHECK 0/1，营业与接单分离 |
| `opening_hours` | VARCHAR(64) | 展示字段 |
| `created_at` / `updated_at` | DATETIME(3) | UTC 语义 |

没有把经纬度存成字符串，也没有用浮点数。DECIMAL 足够表达 6 位小数坐标。

## 3. products

关键列：

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `store_id` | BIGINT | 外键 → `stores.id` |
| `product_code` | VARCHAR(32) | 与 `store_id` 组合唯一 |
| `name` / `description` | VARCHAR | 展示字段 |
| `category_code` / `category_name` | VARCHAR(32) | 当前为展示分类，未单独建分类表 |
| `category_sort` / `sort_order` | SMALLINT / INT | 稳定排序 |
| `price` | DECIMAL(10,2) | CHECK `price > 0` |
| `on_sale` | TINYINT | CHECK 0/1 |

约束：

```text
UNIQUE (store_id, product_code)
FOREIGN KEY (store_id) REFERENCES stores(id)
CHECK (price > 0)
CHECK (on_sale IN (0,1))
```

Why 用 `(store_id, product_code)` 而不是全局 `product_code` 唯一：商品属于具体门店，不同门店可以有各自编码与价格。若业务需要全局 SKU，应作为后续独立重构，不提前假设。

## 4. 菜单索引

```sql
KEY idx_products_store_menu
    (store_id, on_sale, category_sort, sort_order, id)
```

它对应：

```sql
SELECT ...
FROM products
WHERE store_id = 1
  AND on_sale = 1
ORDER BY category_sort, sort_order, id;
```

实际 `EXPLAIN`：

```text
type=ref
key=idx_products_store_menu
key_len=9
ref=const,const
rows=6
Extra=NULL
```

含义：

- `ref` 使用等值条件 `store_id` 与 `on_sale`；
- `key_len=9` 对应 `BIGINT(8) + TINYINT(1)`；
- 排序字段在索引中按顺序排列，未出现额外 filesort；
- 存储引擎预估扫描 6 行，与中央车站店在售商品数量一致。

这不是“所有查询都最优”的证明。A1 数据量很小，也没有分页；索引会随着后续订单查询重新评估。

## 5. 种子数据

| 门店 | 状态 | 总商品 | 在售 |
| --- | --- | --- | --- |
| 中央车站店 | OPEN + accepting | 7 | 6 |
| 滨江店 | OPEN + paused | 6 | 6 |
| 老城店 | CLOSED | 5 | 4 |

总计 18 条商品，16 条在售。中央车站店故意保留一个下架商品“海盐奶昔”，用于验证菜单过滤。

种子数据放在 Flyway 迁移中，是为了让开发库、Testcontainers 和截图使用同一套可复现数据。生产环境不能直接照搬这套 demo seed，后续需要独立的初始化策略。

## 6. 本轮没有建的表

- `stock`：A1 没有库存读写，提前建表无法验证并发规则。
- `orders`、`order_items`、`order_events`：A1 没有订单。
- `users`、`user_store_memberships`：A2 登录与权限时建。
- `idempotency_records`、`outbox_events`：A3/B 有对应写入语义时建。

## 7. 验证命令

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT s.id, s.name, s.business_status, s.accepting_orders,
             COUNT(p.id) AS total_products,
             SUM(p.on_sale) AS on_sale_products
      FROM stores s LEFT JOIN products p ON p.store_id = s.id
      GROUP BY s.id, s.name, s.business_status, s.accepting_orders
      ORDER BY s.id;"
```

执行计划：

```bash
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "EXPLAIN SELECT id, store_id, product_code, name, description,
               category_code, category_name, category_sort, price, on_sale, sort_order
        FROM products
        WHERE store_id = 1 AND on_sale = 1
        ORDER BY category_sort, sort_order, id;"
```
