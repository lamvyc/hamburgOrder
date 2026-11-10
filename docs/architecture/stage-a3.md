# 阶段 A3 架构与事务设计

## 1. 目标

A3 完成一个可运行订单闭环：

1. 顾客从本地购物车提交订单。
2. 服务端只接收门店 ID、商品 ID、数量，重新查询门店、商品、价格与库存。
3. 同一事务创建订单、订单明细、库存预占、库存流水、订单事件和幂等记录。
4. 条件更新库存，检查影响行数。
5. 相同 Idempotency-Key 重放原订单；不同内容返回冲突。
6. 顾客只能读取自己的订单。
7. 前端进入订单详情，显示 WAIT_PAY，支付留到 A4。

## 2. 创建订单调用链

```text
CartView.vue submitOrder
  -> checkout store (persisted Idempotency-Key + cart fingerprint)
  -> POST /api/orders (Session + CSRF + Idempotency-Key)
  -> Security: ROLE_CUSTOMER
  -> OrderController
  -> OrderService.create()
       @Transactional
       1. insert idempotency_records (PROCESSING)
       2. validate store / orderable
       3. reload products (store + on_sale + DB price)
       4. insert orders
       5. insert order_items (snapshots)
       6. for each product sorted by productId:
            UPDATE stock ... WHERE available >= qty
            check affected rows == 1
            insert stock_logs
       7. insert order_events (CREATED, WAIT_PAY)
       8. update idempotency_records COMPLETED
  -> 201 first / 200 replay
  -> OrderDetailView
```

关键文件：

| 层 | 文件 |
| --- | --- |
| Controller | `backend/src/main/java/com/burgerlab/order/OrderController.java` |
| 事务编排 | `backend/src/main/java/com/burgerlab/order/OrderService.java` |
| 库存条件更新 | `backend/src/main/java/com/burgerlab/inventory/StockMapper.java` |
| 库存预占/流水 | `backend/src/main/java/com/burgerlab/inventory/InventoryService.java` |
| 幂等读取 | `backend/src/main/java/com/burgerlab/order/IdempotencyRecordMapper.java` |
| 前端 checkout | `frontend/src/stores/checkout.ts` |
| 订单详情 | `frontend/src/views/OrderDetailView.vue` |
| 迁移 | `V6__create_order_inventory_tables.sql`、`V7__seed_stage_a3_inventory.sql` |

## 3. 事务边界

`OrderService.create()` 是唯一下单事务。`InventoryService.reserve()` 使用：

```java
@Transactional(propagation = Propagation.MANDATORY)
```

Why：如果库存服务自己开新事务，订单可能提交而库存没有提交，或库存提交而订单回滚。MANDATORY 强制它必须加入调用者事务。

任一商品预占失败会抛出业务冲突，整个事务回滚：

- `orders` 不保留；
- `order_items` 不保留；
- 已经更新的前序库存回滚；
- `stock_logs` 不保留；
- `order_events` 不保留；
- `idempotency_records` 不保留，因而可以用同一个 key 重试。

## 4. 库存规则

```sql
UPDATE stock
SET available = available - ?,
    reserved = reserved + ?,
    version = version + 1
WHERE store_id = ?
  AND product_id = ?
  AND available >= ?
```

- `available` 与 `reserved` 必须在同一条 UPDATE 内变化。
- `available >= quantity` 是并发正确性的条件，不先 SELECT 再判断。
- 检查影响行数必须为 1；为 0 表示库存不足或库存行不存在。
- 多商品按 `productId` 升序更新，固定锁顺序，降低多商品订单之间的死锁概率。
- `version` 递增，为后续显式乐观锁/审计保留。
- `stock_logs` 记录本次变化的 before/after 结果与订单号关联。

## 5. 幂等设计

作用域：

```text
(user_id, operation='CREATE_ORDER', idempotency_key)
```

数据库唯一约束：

```sql
UNIQUE KEY uk_idempotency_scope (user_id, operation, idempotency_key)
```

流程：

1. 客户端发送 8–64 位 `Idempotency-Key`。
2. 服务端计算规范化请求哈希：`storeId + 排序后的 productId:quantity`。
3. 事务内先插入 `PROCESSING` 记录。
4. 插入成功：继续建单，最后更新为 `COMPLETED` + `order_id`。
5. 唯一键冲突：等待已存在事务结束后 `SELECT ... FOR UPDATE`：
   - hash 相同且 COMPLETED：返回原订单，HTTP 200，`Idempotent-Replayed: true`；
   - hash 不同：409 `IDEMPOTENCY_CONFLICT`；
   - 仍为 PROCESSING：409 `IDEMPOTENCY_IN_PROGRESS`；
   - 订单缺失/不属于当前用户：内部一致性错误。
6. 业务失败导致整个事务回滚，幂等记录也不存在，重试可以重新执行。

A3 没有实现记录保留策略和清理任务。长期表增长、过期清理、失败重试的异步语义留到后续。

## 6. 为什么不信任客户端价格

请求中没有价格字段：

```json
{
  "storeId": 1,
  "items": [{"productId": 1001, "quantity": 2}]
}
```

服务端：

- 按 productId 查询商品；
- 校验 store_id；
- 校验 on_sale；
- 使用数据库价格；
- 计算 lineAmount 与 totalAmount；
- 订单明细保存商品名、编码、成交单价快照。

本地购物车价格为展示快照，A3 不读取它作为成交依据。

## 7. 订单号、金额与时间

- 订单号：`BO` + UTC 时间 + 6 位随机 hex，数据库唯一约束。
- 金额：`DECIMAL(10,2)` / `BigDecimal`；响应为字符串。
- 订单过期：创建时 `expires_at = now + 30min`，尚无扫描任务。
- 状态：创建为 `WAIT_PAY`；A4 支付进入 `WAIT_MAKE`，A4 工作台进入 `MAKING/READY/COMPLETED`；A5 取消/超时。
- 订单事件：A3 只写 `CREATED`，为 A4/A5 的状态轨迹预留。

## 8. 前端幂等键

`checkout.ts` 使用：

- localStorage key `burger-order.checkout.v1`；
- `fingerprint = storeId + 排序后的 productId:quantity`；
- 同一购物车重试复用 key；
- 购物车变化换新 key；
- 成功后删除 pending key 并清空购物车；
- 网络失败保留 key，用户重试时不会重复建单。

## 9. 权限

- `POST /api/orders`、`GET /api/orders/**`：只允许 ROLE_CUSTOMER。
- 订单详情按 `user_id` 校验；其他顾客返回 404 `ORDER_NOT_FOUND`，避免枚举。
- 员工/管理员不能使用顾客订单接口；A4 会设计独立门店订单接口和门店范围。

## 10. 未验证与边界

- 未实现支付、取消、超时释放、优惠券、订单列表。
- 未实现支付与取消的竞态。
- 未实现幂等记录清理/保留策略。
- 未实现高并发压测、死锁重试、连接池容量评估。
- 当前并发测试证明本机/Testcontainers 下的数据库条件更新和唯一约束，不代表生产容量。

## 11. 下一步 A4

A4 需要：

- WAIT_PAY -> WAIT_MAKE 模拟支付，校验过期；
- 员工所属门店订单列表与数据范围；
- 开始制作 WAIT_MAKE -> MAKING；
- 待取餐 MAKING -> READY；
- 完成 READY -> COMPLETED；
- 每次状态转换的权限、前置状态、条件更新、影响行数、事件同事务；
- 重复/非法操作明确响应。
