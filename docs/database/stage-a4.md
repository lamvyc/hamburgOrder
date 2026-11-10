# 阶段 A4 数据库说明

## 1. V8 迁移

```text
V8__add_payment_transition_support.sql
```

内容：

1. 扩展 `stock_logs.change_type` CHECK，允许 `COMMIT`；
2. 新增员工工作台索引 `idx_orders_store_status_created`。

没有新增 payments 表。

## 2. 支付后的库存状态

下单：

```text
available -= qty
reserved  += qty
```

支付：

```text
reserved -= qty
available 不变
```

A4 实际订单 7：

```text
stock_logs:
RESERVE  available_delta=-1  reserved_delta=+1  available_after=39 reserved_after=9
COMMIT   available_delta=0   reserved_delta=-1  available_after=39 reserved_after=8
```

Why 支付只减 reserved：`available` 已经在下单时表示“未售库存”，支付只是确认预占，不是再次销售。

## 3. 订单事件

A4 实际事件 7：

```text
CREATED            NULL      -> WAIT_PAY   演示顾客
PAYMENT_SUCCEEDED  WAIT_PAY  -> WAIT_MAKE  演示顾客
MAKING_STARTED     WAIT_MAKE -> MAKING     中央车站店员工
READY_FOR_PICKUP   MAKING    -> READY      中央车站店员工
COMPLETED          READY     -> COMPLETED  中央车站店员工
```

员工操作通过 `operator_user_id` 关联 users，查询时 LEFT JOIN 取得 display_name。

## 4. 条件更新

支付：

```sql
UPDATE orders
SET status='WAIT_MAKE', version=version+1, updated_at=?
WHERE id=?
  AND user_id=?
  AND status='WAIT_PAY'
  AND expires_at > ?;
```

员工转换：

```sql
UPDATE orders
SET status=?, version=version+1, updated_at=?
WHERE id=?
  AND store_id=?
  AND status=?;
```

条件中同时包含身份范围与前置状态，影响行数必须为 1。

## 5. 订单工作台索引

```sql
KEY idx_orders_store_status_created (store_id, status, created_at, id)
```

实际查询：

```sql
WHERE store_id = 1
  AND status IN ('WAIT_MAKE','MAKING','READY')
ORDER BY created_at, id
```

实际执行计划：

```text
type=range
key=idx_orders_store_status_created
rows=3
Extra=Using index condition; Using filesort
```

索引用于过滤门店/状态，但跨状态排序仍产生 filesort。当前数据量小；后续活跃订单增长时需重新评估索引或查询形态。A4 不把该计划写成“完全索引排序”。

## 6. payments 表为何仍不存在

A4 没有真实支付回调。为了避免一张没有交易号、回调幂等、对账语义的表，本轮只记录：

- 订单状态；
- PAYMENT_SUCCEEDED 事件；
- stock COMMIT 流水。

支付回调实验再引入 payments。

## 7. 过期订单

A4 只在支付时检查 `expires_at`：

- 过期支付返回 409 `ORDER_EXPIRED`；
- 不自动取消；
- reserved 仍保留，等待 A5 扫描释放。

这是刻意边界，避免 A4 做出半套取消逻辑。

## 8. 验证 SQL

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, order_no, status, total_amount FROM orders ORDER BY id DESC LIMIT 5;
      SELECT order_id, event_type, from_status, to_status, operator_user_id
        FROM order_events WHERE order_id = 7 ORDER BY id;
      SELECT product_id, change_type, available_delta, reserved_delta,
             available_after, reserved_after
        FROM stock_logs WHERE order_id = 7 ORDER BY id;"
```

## 9. 未建立

- payments / refunds；
- 取消/超时扫描索引（A5）；
- 门店订单统计表；
- 管理员管理写表。
