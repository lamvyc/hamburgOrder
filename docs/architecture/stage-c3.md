# 阶段 C3 架构：可观测性与性能基线

## 1. 目标

C3 让系统运行状态可观察：

1. 暴露 Prometheus 文本指标。
2. Spring MVC 自动 HTTP 指标（计数/耗时直方图）。
3. 菜单缓存 HIT/MISS/BYPASS/NEGATIVE/STAMPEDE_WAIT 计数器。
4. 系统状态页显示缓存计数与 Prometheus 链接。
5. 保留 traceId 日志关联。
6. 用真实端点/浏览器验证，不用假数据。

## 2. 指标栈

```text
Micrometer
  ├─ HTTP server metrics (Spring MVC)
  ├─ JVM/Hikari/Redis 等自动指标
  └─ burger.menu.cache.requests{result=hit|miss|bypass|negative|stampede_wait}

Prometheus registry
  └─ GET /actuator/prometheus (text/plain)
```

`management.endpoints.web.exposure.include=health,prometheus,metrics`。

Endpoint access：

```yaml
management.endpoint.prometheus.access: unrestricted
```

注意：`enabled` 与 `access` 是互斥配置，C3 实际踩到过同时设置导致上下文启动失败。

## 3. 缓存计数

`MenuCacheStats`：

- `LongAdder` 进程内快照；
- 同时递增 Micrometer `Counter`；
- `/api/system/menu-cache` 返回快照；
- 重启归零。

标签：

```text
result=hit
result=miss
result=bypass
result=negative
result=stampede_wait
```

标签值来自受控枚举，不使用用户输入/门店 ID 作为标签，避免基数爆炸。

## 4. HTTP 指标

Spring Boot 自动生成：

```text
http_server_requests_seconds_count
http_server_requests_seconds_sum
http_server_requests_seconds_bucket
```

标签包含 method/status/uri 等；可用于 P95/P99 与错误率基线。

未接入 Grafana/Alertmanager；C3 只提供端点与文档。

## 5. 系统页

`/system`：

- 应用/DB 健康；
- HIT/MISS/BYPASS/NEGATIVE/STAMPEDE WAIT；
- Prometheus 链接；
- 阶段 C3 标签；
- 页面文案同步当前工程范围，不再写“订单未接入”。

## 6. 性能基线

C3 没有做正式压测；Prometheus 直方图提供了后续 P95/P99 的数据源。

未建立：

- SLO/错误预算；
- 告警规则；
- 分布式追踪（仅 traceId 日志）；
- 指标持久化/多实例聚合。

## 7. 安全

Actuator metrics/prometheus 当前公开，便于本地观测；生产应限制网络/角色。

## 8. 边界

- 进程本地计数；
- 无 Grafana；
- 无 Alertmanager；
- 无 OpenTelemetry tracing；
- 无压测脚本/容量结论。
