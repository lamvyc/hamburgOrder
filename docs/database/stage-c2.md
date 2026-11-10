# 阶段 C2 数据库/Redis 说明

## 1. 迁移

C2 没有新增 Flyway 迁移，仍为 V13。

## 2. Redis Key

正常菜单：

```text
menu:v1:{storeId} = MenuResponse JSON
TTL = 60 + random(0..15) 秒
```

负缓存：

```text
menu:v1:{storeId} = __MISSING__
TTL = 10 秒
```

计数不存 Redis，而是进程内 `LongAdder`；重启归零。

## 3. 配置

```yaml
app.cache.menu:
  enabled: true
  ttl-seconds: 60
  ttl-jitter-seconds: 15
  negative-ttl-seconds: 10
```

环境变量：`MENU_CACHE_ENABLED`、`MENU_CACHE_TTL_SECONDS`、`MENU_CACHE_TTL_JITTER_SECONDS`、`MENU_CACHE_NEGATIVE_TTL_SECONDS`。

## 4. 验证

```bash
docker compose ... exec -T redis redis-cli TTL menu:v1:1
docker compose ... exec -T redis redis-cli TTL menu:v1:999
docker compose ... exec -T redis redis-cli GET menu:v1:999
curl -s http://127.0.0.1:8080/api/system/menu-cache
```

预期：

- 正常 TTL `0 < TTL <= 75`；
- 缺失 TTL `0 < TTL <= 10`；
- value `__MISSING__`；
- 计数包含 hit/miss/negative/stampedeWait。

## 5. 测试隔离

非缓存测试：

```text
app.cache.menu.enabled=false
```

C2 测试使用独立 Redis Testcontainer，避免污染开发 Redis。

## 6. 未建立

- Redis 分布式锁；
- 多实例计数聚合；
- 指标持久化；
- 负缓存主动清理；
- 布隆过滤器。
