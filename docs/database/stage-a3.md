# 阶段 A3 数据库说明

## 1. 迁移

```text
V6__create_order_inventory_tables.sql
V7__seed_stage_a3_inventory.sql
```

V1–V5 保持不修改。

## 2. orders

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `order_no` | VARCHAR(32) | 唯一业务订单号 |
| `user_id` | BIGINT | 顾客外键 |
| `store_id` | BIGINT | 门店外键 |
| `status` | VARCHAR(16) | WAIT_PAY 等状态，CHECK 限制 |
| `currency` | CHAR(3) | 当前 CNY |
| `total_amount` | DECIMAL(10,2) | 服务端成交总额 |
| `expires_at` | DATETIME(3) | 创建后 30 分钟 |
| `version` | INT | 预留版本字段 |
| `created_at` / `updated_at` | DATETIME(3) | UTC |

索引：

```text
UNIQUE (order_no)
(user_id, created_at, id)
```

A3 没有门店订单列表和过期扫描，所以没有提前添加 `(store_id,status,created_at,id)` 与 `(status,expires_at,id)`；它们应在 A4/A5 真实 SQL 与 `EXPLAIN` 出现后添加。

## 3. order_items

保存成交快照：

- product_id；
- product_code；
- product_name；
- unit_price；
- quantity；
- line_amount；
- order_id。

约束：

```text
UNIQUE (order_id, product_id)
CHECK quantity BETWEEN 1 AND 99
CHECK unit_price > 0
CHECK line_amount > 0
FOREIGN KEY order_id -> orders
FOREIGN KEY product_id -> products
```

商品名称和价格是成交快照，不依赖后续商品改价。

## 4. stock

```text
PRIMARY KEY (store_id, product_id)
available INT NOT NULL
reserved  INT NOT NULL
version   INT NOT NULL
CHECK available >= 0
CHECK reserved >= 0
```

A3 规则：

- 下单：available 减少、reserved 增加。
- 支付：reserved 减少（A4）。
- 取消/超时：reserved 减少、available 增加（A4/A5）。

库存更新 SQL：

```sql
UPDATE stock
SET available = available - ?,
    reserved = reserved + ?,
    version = version + 1
WHERE store_id = ?
  AND product_id = ?
  AND available >= ?;
```

实际 `EXPLAIN`：

```text
type=range
key=PRIMARY
key_len=16
ref=const,const
rows=1
Extra=Using where
```

条件包含 `available >= 1`，影响行数必须为 1。

## 5. stock_logs

A3 记录 `RESERVE`：

- order_id；
- store_id / product_id；
- available_delta / reserved_delta；
- available_after / reserved_after；
- reason；
- created_at。

`change_type` CHECK 允许 RESERVE/RELEASE/ADJUST，为 A4/A5 释放库存预留。

## 6. orders / order_items / order_events 的关系

`order_events` 保存：

- event_type；
- from_status / to_status；
- operator_user_id；
- reason；
- created_at。

A3 只写：

```text
CREATED, from=null, to=WAIT_PAY
```

状态转换历史在 A4/A5 继续使用同一张表。

## 7. idempotency_records

```text
UNIQUE (user_id, operation, idempotency_key)
request_hash CHAR(64)
status PROCESSING/COMPLETED
order_id NULL -> orders
created_at / completed_at
```

A3 在同一个订单事务内插入 PROCESSING，最后更新 COMPLETED。失败时整个记录回滚，允许同 key 重试。

未实现清理策略。

## 8. 种子

V7 增加：

- 第二条顾客 `demo_customer_two`，用于订单归属测试；
- 18 条 stock 行；
- 所有商品初始 available 与 reserved=0。

库存不是通过应用启动脚本写入，而是 Flyway 可复现种子。

## 9. 验证

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, order_no, status, total_amount, expires_at FROM orders ORDER BY id;
      SELECT product_id, available, reserved, version FROM stock WHERE product_id IN (1001,1006);
      SELECT idempotency_key, status, order_id FROM idempotency_records;"
```

## 10. 未建立

- payments（A4）
- 优惠券关联
- 超时扫描索引（A5）
- 订单列表/门店工作台索引（A4）
- 对账/清理表
