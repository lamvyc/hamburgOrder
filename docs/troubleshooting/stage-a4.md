# 阶段 A4 排错入口

学习故障练习在 [阶段 A4 学习材料](../learning/stage-a4.md)。

## 1. 先区分状态码

| HTTP | code | 含义 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 未登录 |
| 403 | `FORBIDDEN` | 角色不足/CSRF |
| 404 | `ORDER_NOT_FOUND` | 订单不存在或不属于当前顾客/员工门店 |
| 409 | `ORDER_EXPIRED` | 支付时已过期 |
| 409 | `ORDER_STATUS_CONFLICT` | 前置状态不合法/状态已被改变 |
| 409 | `STOCK_RESERVATION_BROKEN` | 支付提交 reserved 时数据不一致 |

## 2. 支付失败

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, user_id, status, expires_at, updated_at FROM orders WHERE id = ?;
      SELECT product_id, available, reserved, version FROM stock WHERE product_id IN (1001,1006);
      SELECT event_type, from_status, to_status, operator_user_id
        FROM order_events WHERE order_id = ? ORDER BY id;
      SELECT change_type, available_delta, reserved_delta
        FROM stock_logs WHERE order_id = ? ORDER BY id;"
```

- `ORDER_EXPIRED`：订单状态应仍为 WAIT_PAY，reserved 未释放，等 A5。
- `ORDER_STATUS_CONFLICT`：检查当前 status 是否已 WAIT_MAKE。
- `STOCK_RESERVATION_BROKEN`：检查 reserved 是否足够、订单明细数量是否一致。
- 支付成功：应看到 PAYMENT_SUCCEEDED 且 stock_logs COMMIT，available 不变、reserved 减少。

## 3. 员工看不到订单

- 员工是否属于该门店：`user_store_memberships`；
- 是否请求了正确的 `storeId`；
- 订单是否活跃（WAIT_MAKE/MAKING/READY），COMPLETED 不在工作台；
- 是否已支付：WAIT_PAY 不在员工活跃列表。

```sql
SELECT u.username, m.store_id
FROM users u LEFT JOIN user_store_memberships m ON m.user_id=u.id
WHERE u.username='demo_staff';
```

## 4. 员工操作 409

- 重复开始制作：订单已经是 MAKING；
- 跳过开始制作直接 ready：前置不合法；
- 重复完成：已 COMPLETED；
- 两个员工同时点：一个成功，另一个 409。

检查 order_events 的 from/to 与操作者。

## 5. 员工操作 404

不是“订单肯定不存在”：
- 订单可能属于其他门店；
- 设计上对无权限门店统一 404，避免泄露订单存在性。

不要为了“更友好”改成返回其他门店订单内容。

## 6. 重复事件

如果状态只转换一次但事件重复：

- 检查事件是否在状态条件更新之后写入；
- 检查事件插入是否与状态转换同一事务；
- 检查是否有重试/重复请求没有被条件更新拦住；
- 检查是否存在“先写事件、后更新状态”的错误顺序。

A4 没有为事件加唯一键，因为合法状态机下同一转换只应发生一次；如果出现重复，必须从条件更新/事务/幂等层面修复，而不是只删重复数据。

## 7. 订单详情事件不显示

- `/api/orders/{id}/events` 是否 200；
- 当前用户是否为订单所有者；
- 事件查询是否 LEFT JOIN 到 operator display_name；
- 前端是否在支付后重新拉取事件。

## 8. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:a4
```

A4 并发测试验证最终订单状态、COMMIT 流水数量和 PAYMENT_SUCCEEDED 数量，不只看接口返回。
