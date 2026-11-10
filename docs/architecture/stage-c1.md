# 阶段 C1 架构：门店菜单 Redis 缓存

## 1. 目标

C1 给公开菜单增加 cache-aside：

1. Redis 缓存 `GET /api/stores/{id}/menu`。
2. Key `menu:v1:{storeId}`。
3. TTL 默认 60 秒，可配置。
4. 首请求 MISS，后续 HIT。
5. 管理员改价/上下架后，事务提交后失效。
6. Redis 不可用时 BYPASS，直接读 MySQL，接口仍可用。
7. 响应头 `X-Cache: HIT|MISS|BYPASS`。
8. 缓存关闭开关用于测试隔离/降级。

## 2. 数据流

```text
MenuController
  -> MenuCacheService.getMenu(storeId)
       enabled=false -> DB, BYPASS
       Redis GET
         hit  -> JSON -> MenuResponse, HIT
         miss -> CatalogService.getMenu(DB)
                 JSON SET EX ttl, MISS
         error -> DB, BYPASS
```

菜单只包含门店 + 上架商品 + 价格，不包含库存。因此：

- 订单库存变化不失效菜单；
- 管理员改价/上下架必须失效；
- 门店营业状态修改尚未实现，后续也要失效。

## 3. 失效时机

`AdminInventoryService.updateProduct` 在同一事务更新商品后：

```text
menuCacheService.evictAfterCommit(storeId)
```

- 有事务：注册 `afterCommit` 删除 key；
- 无事务：立即删除；
- 删除失败只记录日志，靠 TTL 兜底。

不在事务中途删除，避免并发请求把旧数据重新写回缓存。

## 4. 降级

- Redis 读/写异常不冒泡到公开菜单；
- 读失败：BYPASS 查 DB；
- 写失败：返回 DB 结果；
- 失效失败：TTL 到期自愈；
- 损坏 JSON：删除 key 后回源。

## 5. 测试隔离

非缓存集成测试统一：

```text
app.cache.menu.enabled=false
```

否则会连接开发 Redis，把测试 MySQL 的菜单写进开发缓存。

C1 专用测试使用独立 Redis Testcontainer；菜单缓存测试仍开启缓存。

## 6. 前端/观测

菜单页面本身无感；浏览器/网关可通过 `X-Cache` 观察：

```text
first  -> MISS
second -> HIT
Redis down -> BYPASS
```

C1 E2E 先 FLUSHDB，再请求两次并断言 MISS→HIT。

## 7. 未实现

- 缓存穿透（空值缓存/布隆过滤器）；
- 缓存击穿（分布式锁/single-flight）；
- 缓存雪崩（TTL 抖动）；
- 多实例广播失效；
- 菜单版本号/事件驱动失效；
- 指标/命中率看板；
- 门店状态修改的失效。
