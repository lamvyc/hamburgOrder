# 阶段 A5 排错入口

学习故障练习在 [阶段 A5 学习材料](../learning/stage-a5.md)。

## 1. 取消返回 409

- 订单已支付（WAIT_MAKE/MAKING/READY）；
- 已完成；
- 已被超时扫描或另一次取消处理；
- 重复点击。

检查：

```sql
SELECT id, status, expires_at, updated_at FROM orders WHERE id=?;
SELECT event_type, from_status, to_status, operator_user_id, reason
FROM order_events WHERE order_id=? ORDER BY id;
```

## 2. 取消后库存不对

```sql
SELECT product_id, available, reserved, version FROM stock WHERE store_id=? AND product_id=?;
SELECT change_type, available_delta, reserved_delta, available_after, reserved_after
FROM stock_logs WHERE order_id=? ORDER BY id;
```

WAIT_PAY 取消应有：

```text
RESERVE  -qty / +qty
RELEASE  +qty / -qty
```

最终 available 回到下单前，reserved 回到 0。若出现两次 RELEASE，检查取消是否绕过了条件状态更新。

## 3. 超时扫描没有执行

检查：

- `app.order.timeout-scan.enabled`；
- initial delay / fixed delay；
- 订单是否真的 `expires_at <= now` 且 status=WAIT_PAY；
- 日志 `timeout_cancel_batch`；
- 数据库时间是否 UTC；
- 是否被支付/手动取消抢先。

手工只读检查：

```sql
SELECT id, status, expires_at, UTC_TIMESTAMP(3) AS now_utc
FROM orders
WHERE status='WAIT_PAY'
ORDER BY expires_at, id;
```

不要通过改系统时钟测试；使用过期数据与传入 cutoff。

## 4. 重复扫描

预期：

- 第一次 affected=1；
- 第二次 affected=0；
- 只有一条 RELEASE；
- 只有一条 CANCELLED 事件。

若第二条订单重复释放，检查扫描是否先 SELECT 然后无条件 UPDATE。

## 5. 支付/取消都“成功”

不可能同时合法。检查：

- 两个操作是否都更新同一行状态且 WHERE 有 status；
- 是否在状态更新后没有检查 affected rows；
- 失败方是否仍调用了库存 COMMIT/RELEASE；
- 是否能通过 `orders.status` 与 stock_logs 唯一终态证明。

最终只允许：

```text
WAIT_MAKE + COMMIT
或
CANCELLED + RELEASE
```

## 6. 权限错误

| 场景 | HTTP |
| --- | --- |
| 本人取消 WAIT_PAY | 200 |
| 其他顾客取消 | 404 |
| 员工取消 | 403 |
| 未登录 | 401 |
| 重复/非法状态 | 409 |

员工不能通过传入订单 ID 取消顾客订单。

## 7. 索引

```sql
EXPLAIN SELECT id FROM orders
WHERE status='WAIT_PAY' AND expires_at <= UTC_TIMESTAMP(3)
ORDER BY expires_at, id
LIMIT 100;
```

应使用 `idx_orders_status_expires`，当前实际 `Using index`，无 filesort。

## 8. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:a5
```

测试通过标准是订单状态、库存终态、RELEASE 数量、CANCELLED 事件数量同时正确，不是“接口返回 200”。
