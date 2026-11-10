# 阶段 C1 数据库/Redis 说明

## 1. MySQL

C1 没有新增 Flyway 迁移，仍为 V13。菜单缓存不改变数据库事实来源。

## 2. Redis

服务：

```text
redis:7.4-alpine
127.0.0.1:16379 -> 6379
```

配置：

```yaml
spring.data.redis:
  host: 127.0.0.1
  port: 16379
  timeout: 300ms
  connect-timeout: 300ms

app.cache.menu:
  enabled: true
  ttl-seconds: 60
```

Key：

```text
menu:v1:{storeId}
```

Value：`MenuResponse` JSON。

TTL：`app.cache.menu.ttl-seconds`。

## 3. 失效

管理员改价/上下架事务提交后：

```text
DEL menu:v1:{storeId}
```

若 Redis 不可用：

- 删除失败日志；
- TTL 到期自愈；
- 菜单读取 BYPASS DB。

## 4. 验证

```bash
docker compose --env-file .env -f deploy/compose.yaml exec -T redis redis-cli KEYS 'menu:v1:*'
docker compose --env-file .env -f deploy/compose.yaml exec -T redis redis-cli TTL menu:v1:1
docker compose --env-file .env -f deploy/compose.yaml exec -T redis redis-cli GET menu:v1:1
```

E2E 前：

```bash
docker compose ... exec -T redis redis-cli FLUSHDB
```

## 5. 测试隔离

非缓存集成测试：

```text
app.cache.menu.enabled=false
```

C1 集成测试：

- MySQL Testcontainer；
- Redis Testcontainer；
- 独立端口；
- 测试前 FLUSHDB。

## 6. 未建立

- 空值缓存；
- 分布式锁；
- TTL 抖动；
- 多实例失效；
- 缓存指标。
