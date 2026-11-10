# 阶段 A2 数据库说明

## 1. 迁移

```text
V1__create_system_metadata.sql
V2__create_store_catalog.sql
V3__seed_stage_a1_catalog.sql
V4__create_auth_tables.sql
V5__seed_stage_a2_auth_users.sql
```

V1–V3 未修改。

## 2. users

| 列 | 类型 | 约束 |
| --- | --- | --- |
| `id` | BIGINT | 主键，自增 |
| `username` | VARCHAR(64) | 唯一，非空 |
| `password_hash` | VARCHAR(100) | BCrypt，非空 |
| `display_name` | VARCHAR(80) | 非空 |
| `role` | VARCHAR(16) | CHECK CUSTOMER/STAFF/ADMIN |
| `enabled` | TINYINT | CHECK 0/1 |
| `created_at` / `updated_at` | DATETIME(3) | UTC 语义 |

不存明文密码，不存 Session。Session 当前在 Tomcat 内存中，应用重启会失效。

## 3. user_store_memberships

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | BIGINT | 外键 users |
| `store_id` | BIGINT | 外键 stores |
| `created_at` | DATETIME(3) | 创建时间 |

约束：

```text
PRIMARY KEY (user_id, store_id)
KEY (store_id, user_id)
```

员工工作区按 `user_id` 查询；复合索引的后半部分为将来按门店反查员工预留。

## 4. 固定账号

| id | username | role | membership |
| --- | --- | --- | --- |
| 1 | demo_customer | CUSTOMER | 无 |
| 2 | demo_staff | STAFF | 门店 1 |
| 3 | demo_admin | ADMIN | 无 |

密码均为 `Burger@123`，BCrypt cost 12，使用不同 salt。`password_hash` 前缀实际为 `$2y$12$`。

种子数据只用于本地开发与测试；生产必须替换账号、密码和初始化策略。

## 5. 为什么不建购物车表

- A2 明确采用 Pinia + localStorage；
- 没有跨设备同步需求；
- 本地价格不是成交依据；
- 没有结算接口，建购物车表只会制造“已实现”的错觉。

A3 下单接口接收商品 ID/数量，服务端重新查询门店、商品、上架、价格与库存，不需要信任本地购物车。

## 6. 为什么不建 orders/stock

A2 没有创建订单和扣库存行为。提前建表无法验证事务、状态机、幂等、索引与并发条件更新，留到 A3/B。

## 7. 验证 SQL

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, username, display_name, role, enabled, LEFT(password_hash, 7) AS hash_prefix FROM users ORDER BY id;
      SELECT user_id, store_id FROM user_store_memberships ORDER BY user_id, store_id;"
```

预期：

```text
demo_customer  CUSTOMER
demo_staff     STAFF    user_id=2 store_id=1
demo_admin     ADMIN
```

## 8. 安全注意

- 不要把真实密码写进 Flyway 种子。
- 不要让应用日志打印 `password_hash` 或明文密码。
- 管理员/员工账号的插入与绑定应有独立管理流程，不能复用 demo seed。
- 生产应限制数据库账号权限，并启用备份、审计和密钥管理。

> A3 的 V7 迁移额外增加了第二条顾客 `demo_customer_two`，用于订单归属隔离测试。A2 的历史记录仍保留当时 3 个账号的事实。
