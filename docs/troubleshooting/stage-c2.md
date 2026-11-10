# 阶段 C2 排错入口

学习故障练习在 [阶段 C2 学习材料](../learning/stage-c2.md)。

## 1. stampedeWait 很高

- 是否同一门店瞬时大量 MISS；
- 回源是否慢；
- 等待 500ms 是否经常超时；
- 是否多实例各自等待；
- 是否缓存写入失败，导致每次都 MISS。

## 2. 多次回源

单实例：

- local single-flight map 是否有竞态；
- 回源是否在抢到 leader 后做 double-check；
- 回源失败是否 completeExceptionally/remove；
- followers 超时是否直接回源。

多实例：

- 本地 single-flight 不跨实例；
- 需要 Redis 分布式锁/队列。

## 3. 负缓存导致门店创建后 404

- 负 TTL 10s；
- 门店创建后是否主动 DEL `menu:v1:{id}`；
- 管理端失效是否覆盖负 key。

## 4. TTL 抖动

```bash
redis-cli TTL menu:v1:1
```

预期 0–75s。若 TTL 固定 60，检查 jitter 配置；若 TTL 很大，检查配置单位。

## 5. 统计端点

```http
GET /api/system/menu-cache
```

- 进程本地，重启归零；
- 非多实例聚合；
- 没有用户/门店数据。

## 6. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

docker compose ... exec -T redis redis-cli FLUSHDB
cd frontend && pnpm test:e2e:c2
```
