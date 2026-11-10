# 阶段 C2 API 契约

## 1. 缓存统计

```http
GET /api/system/menu-cache
```

公开只读，返回进程本地计数：

```json
{
  "hit": 1,
  "miss": 1,
  "bypass": 0,
  "negative": 2,
  "stampedeWait": 4
}
```

- 重启归零；
- 不是多实例聚合；
- 不包含门店/用户数据。

## 2. 菜单头

不变：

```text
X-Cache: HIT | MISS | BYPASS
```

负缓存命中会走 404 `STORE_NOT_FOUND`，不返回 200。

## 3. 配置

```yaml
app.cache.menu:
  enabled: true
  ttl-seconds: 60
  ttl-jitter-seconds: 15
  negative-ttl-seconds: 10
```

## 4. 行为

- 并发 MISS：单实例只回源一次，其余等待；
- 等待超时：BYPASS 回源；
- 缺失门店：负缓存 10s；
- Redis 故障：BYPASS，不改变菜单错误语义。

## 5. 未实现

- 分布式 single-flight；
- 多实例计数；
- 命中率历史；
- 负缓存管理端点。
