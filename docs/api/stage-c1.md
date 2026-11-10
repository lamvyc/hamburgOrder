# 阶段 C1 API 契约

## 1. 菜单缓存头

```http
GET /api/stores/{storeId}/menu
```

200 响应新增：

```text
X-Cache: HIT | MISS | BYPASS
```

- `MISS`：缓存未命中，已回源并尝试写入；
- `HIT`：直接返回 Redis 缓存；
- `BYPASS`：缓存关闭或 Redis 不可用，直接读 MySQL。

响应体不变。

## 2. 配置

```yaml
app.cache.menu:
  enabled: true
  ttl-seconds: 60
```

`MENU_CACHE_ENABLED=false` 可关闭缓存；`MENU_CACHE_TTL_SECONDS` 改 TTL。

## 3. 一致性

管理员 `PATCH /api/admin/products/{id}` 成功提交后失效该门店菜单。

## 4. 错误

缓存故障不改变菜单 API 错误语义：

- 门店不存在仍 404；
- 数据库不可用仍 503；
- Redis 故障不使菜单失败。

## 5. 未实现

- 缓存管理端点；
- 命中率指标；
- 空值缓存/布隆过滤器；
- single-flight；
- 多实例失效广播。
