# 阶段 A5 数据库说明

## 1. V9 迁移

```text
V9__add_timeout_scan_index.sql
```

```sql
CREATE INDEX idx_orders_status_expires
    ON orders (status, expires_at, id);
```

没有新增表。取消复用 orders、order_items、stock、stock_logs、order_events。

## 2. 取消库存语义

WAIT_PAY 取消：

```text
reserved  -= qty
available += qty
```

与支付相反：

- 支付：reserved -= qty，available 不变；
- 取消：reserved -= qty，available += qty。

stock_logs：

```text
RELEASE available_delta=+qty reserved_delta=-qty
```

条件：

```sql
UPDATE stock
SET available = available + ?,
    reserved = reserved - ?,
    version = version + 1
WHERE store_id = ?
  AND product_id = ?
  AND reserved >= ?;
```

影响行数必须为 1。

## 3. 超时扫描实际结果

开发库中真实扫描把过期订单 1、2、3 取消：

```text
CANCELLED  from WAIT_PAY  operator_user_id=NULL  reason=支付超时自动取消
```

库存流水出现 RELEASE，available 增加、reserved 减少。扫描日志在应用终端按 `timeout_cancel_batch` 记录。

## 4. 超时查询执行计划

```sql
EXPLAIN SELECT id
FROM orders
WHERE status='WAIT_PAY'
  AND expires_at <= UTC_TIMESTAMP(3)
ORDER BY expires_at, id
LIMIT 100;
```

实际：

```text
type=range
key=idx_orders_status_expires
key_len=73
rows=1
Extra=Using where; Using index
```

没有 filesort，符合 `(status, expires_at, id)` 的索引顺序。

## 5. 订单事件

取消事件仍写 order_events：

- event_type=CANCELLED；
- from_status=WAIT_PAY；
- to_status=CANCELLED；
- operator_user_id：顾客取消为 user id；超时为 NULL；
- reason：顾客取消订单 / 支付超时自动取消；
- created_at。

## 6. 重复扫描

订单条件更新：

```sql
UPDATE orders
SET status='CANCELLED'
WHERE id=? AND status='WAIT_PAY' AND expires_at <= ?;
```

第一次 affected=1；第二次 affected=0，不会再次写 RELEASE 或 CANCELLED 事件。

## 7. 数据一致性检查

```sql
SELECT o.id, o.status,
       SUM(CASE WHEN l.change_type='RESERVE' THEN l.reserved_delta ELSE 0 END) AS reserved_in,
       SUM(CASE WHEN l.change_type IN ('COMMIT','RELEASE') THEN l.reserved_delta ELSE 0 END) AS reserved_out
FROM orders o
LEFT JOIN stock_logs l ON l.order_id=o.id
GROUP BY o.id, o.status;
```

对 CANCELLED/COMPLETED 订单，reserved_in + reserved_out 应为 0。

## 8. 未建立

- 优惠券释放；
- 退款/对账；
- 超时扫描审计表；
- 多实例协调表；
- payments。
