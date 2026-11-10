# 阶段 C1 排错入口

学习故障练习在 [阶段 C1 学习材料](../learning/stage-c1.md)。

## 1. 菜单一直是旧价格

```bash
docker compose ... exec -T redis redis-cli TTL menu:v1:1
docker compose ... exec -T redis redis-cli GET menu:v1:1
```

- 管理更新后 key 是否删除？
- 删除是否在事务提交后？
- 是否删除后又被并发旧读写回？
- 是否有多个 Redis/多个 key 前缀？
- 网关/CDN 是否另有缓存？

## 2. 菜单 X-Cache 一直 MISS

- 写入是否失败（序列化/Redis）？
- key 是否马上被删除？
- TTL 是否极短？
- 每次请求 storeId 是否变化？
- Redis 是否连接到了不同实例/DB？

## 3. X-Cache=BYPASS

- `app.cache.menu.enabled=false`；
- Redis 连接失败；
- Redis 超时；
- 序列化/反序列化异常。

BYPASS 是降级成功，不是错误；检查日志 `menu_cache_*_failed`。

## 4. Redis 容器启动但后端连不上

- 端口映射 `16379:6379`；
- `spring.data.redis.port`；
- Docker context/网络；
- 超时 300ms 是否过短（本地应足够）。

## 5. 测试污染开发 Redis

非缓存测试必须：

```text
app.cache.menu.enabled=false
```

否则测试 MySQL 的菜单会写进开发 Redis。C1 集成测试使用独立 Redis Testcontainer，并在测试前 FLUSHDB。

## 6. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

docker compose ... exec -T redis redis-cli FLUSHDB
cd frontend && pnpm test:e2e:c1
```
