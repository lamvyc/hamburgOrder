# 阶段 0 数据库说明

## 1. 唯一迁移来源

```text
backend/src/main/resources/db/migration/V1__create_system_metadata.sql
```

README、架构文档和排错文档只引用它，不维护另一套建表 SQL。后续所有 schema 变更都新增 `V2__...sql`、`V3__...sql`，不修改已经执行过的迁移。

## 2. 本轮表结构

```sql
CREATE TABLE system_metadata (
    meta_key   VARCHAR(64)  NOT NULL,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (meta_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO system_metadata (meta_key, meta_value)
VALUES ('schema_ready', '1');
```

设计理由：

- `meta_key` 主键防止同一个键重复写入。
- `meta_value` 非空，迁移标记不能是“存在但值为空”。
- `updated_at` 使用毫秒精度，统一 UTC 语义。
- 表使用 InnoDB，后续需要事务与外键时不需要换引擎。
- `utf8mb4` 支持中文、emoji 与完整 Unicode；`utf8mb4_0900_ai_ci` 是 MySQL 8 的稳定排序规则。

## 3. `schema_ready` 是否多余

Flyway 的 `flyway_schema_history` 已经记录迁移。`schema_ready` 仍然有价值：

- 它是一张**业务可见**的表，验证应用 Mapper 能读取预期 schema，而不只是 Flyway 自己能写历史表；
- 它提供明确的语义标记，未来可以在迁移中控制“应用是否允许接流量”；
- 它可以在后续健康检查中区分“迁移历史存在”与“应用数据就绪”。

但两者不能互相替代：

- 只有 marker：无法知道迁移版本。
- 只有 history：无法证明业务表访问链路正常。

阶段 0 同时读取两者，属于最小但有意义的双重证据。

## 4. Flyway 配置

`backend/src/main/resources/application.yml`：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    clean-disabled: true
    connect-retries: 5
    connect-retries-interval: 3
```

- `validate-on-migrate`：启动时校验历史迁移 checksum。
- `clean-disabled`：禁止误执行 `flyway clean` 清库。
- `connect-retries`：容器刚启动时给数据库一点就绪窗口。
- Flyway 版本显式覆盖为 11.20.3。Boot 3.5.16 默认的 11.7.2 只声明 MySQL 支持到 8.1；11.20.3 的 MySQL adapter 声明支持到 9.4，并由真实 MySQL 8.4.13 集成测试验证。

## 5. 实测数据库参数

```text
version          8.4.13
character_set    utf8mb4
collation        utf8mb4_0900_ai_ci
time_zone        +00:00
```

验证命令：

```bash
docker compose --env-file .env -f deploy/compose.yaml exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT VERSION(); SELECT @@character_set_server, @@collation_server, @@time_zone;"
```

## 6. 迁移与探活验证

```bash
docker compose --env-file .env -f deploy/compose.yaml exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT * FROM system_metadata;"
```

预期：

```text
schema_ready    1
```

```bash
docker compose --env-file .env -f deploy/compose.yaml exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

预期至少有一条 `version=1`、`success=1` 的记录。

## 7. 本轮没有建立的数据表

`users`、`stores`、`products`、`stock`、`orders`、`order_items`、`order_events`、`coupon_templates`、`user_coupons`、`idempotency_records`、`outbox_events` 都留到有实际业务行为时再建立。

原因：本阶段没有对应的 Controller / Service / Mapper。提前建表无法验证约束是否合理，也容易在后续需求变化时留下错误的索引、外键和状态字段。

## 8. 后续数据库约束的预留原则

阶段 A 起至少需要考虑：

- `stock(store_id, product_id)` 唯一；
- 幂等作用域 `(user_id, operation, idempotency_key)` 唯一；
- `outbox_events.event_id` 唯一；
- 库存 `available >= 0`、`reserved >= 0`；
- 订单状态与 `expires_at` 的索引；
- `orders(store_id, status, created_at, id)`、`orders(user_id, created_at, id)`、`orders(status, expires_at, id)` 按实际 SQL 与 `EXPLAIN` 决定。

这些不在阶段 0 建表，因为还没有真实查询可以验证索引。
