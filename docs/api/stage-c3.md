# 阶段 C3 可观测性契约

## 1. Prometheus

```http
GET /actuator/prometheus
Accept: text/plain
```

公开（本地实验）；生产应限制网络/角色。

包含：

- HTTP 请求计数/耗时直方图；
- JVM/Hikari/Redis 自动指标（如可用）；
- 菜单缓存计数器。

## 2. 缓存统计

```http
GET /api/system/menu-cache
```

```json
{"hit":4,"miss":2,"bypass":0,"negative":4,"stampedeWait":8}
```

进程本地，重启归零。

## 3. 系统页

`/system` 显示缓存计数与 Prometheus 链接。

## 4. 未实现

- 指标写 OpenAPI（Actuator 不属于业务 API）；
- SLO/告警；
- 多实例聚合；
- Grafana；
- 分布式追踪。
