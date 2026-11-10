# 阶段 C2 架构：缓存硬化

## 1. 目标

C2 在 C1 cache-aside 上增加：

1. 本地 single-flight：同实例同门店并发 MISS 只回源一次。
2. 负缓存：不存在门店短 TTL 缓存，防穿透。
3. TTL 抖动：60s 基础上随机 0–15s，降低雪崩风险。
4. 进程内缓存计数：hit/miss/bypass/negative/stampedeWait。
5. `GET /api/system/menu-cache` 暴露计数。
6. 保留 Redis 故障 BYPASS。

## 2. single-flight

`ConcurrentHashMap<storeId, CompletableFuture<CachedMenu>>`：

```text
cache miss
  -> putIfAbsent 抢 leader
  -> 抢到：double-check cache，回源 MySQL，写缓存，complete
  -> 未抢到：等待 leader 最多 500ms
       成功 -> 复用同一 MenuResponse
       超时/失败 -> BYPASS 回源（避免无限等待）
```

统计 `stampedeWait`。

### 边界

这是**单实例** single-flight。多实例仍可能各回源一次；未实现 Redis 分布式锁/跨实例 single-flight。

## 3. 负缓存

`CatalogService.getMenu` 对不存在门店抛 `ResourceNotFoundException`。

`MenuCacheService` 捕获后写：

```text
key = menu:v1:{storeId}
value = __MISSING__
TTL = negative-ttl-seconds = 10
```

后续请求直接抛同样的 404，不再查 MySQL；仍返回 `X-Cache: MISS/BYPASS` 语义由入口决定（负缓存命中不计 HIT，计 negative）。

## 4. TTL 抖动

```text
ttl = 60 + random(0..15) 秒
```

避免同一批 key 同时过期。测试只断言 `0 < TTL <= 75`，不断言随机分布。

## 5. 指标

进程内 `LongAdder`：

- hit；
- miss；
- bypass；
- negative；
- stampedeWait。

端点：

```http
GET /api/system/menu-cache
```

这是进程本地计数，不是多实例聚合；重启归零。

## 6. 失效

管理员改价/上下架仍在事务 `afterCommit` 删除 key；负缓存 key 也会被同一 DEL 清掉（若门店后来创建）。

## 7. 一致性/边界

未实现：

- Redis 分布式 single-flight；
- 多实例失效广播；
- 命中率持久化/看板；
- TTL 抖动加密随机；
- 负缓存创建门店后的主动清理（依赖 TTL/管理端失效）；
- 布隆过滤器。
