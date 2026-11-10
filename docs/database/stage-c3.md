# 阶段 C3 数据库/指标说明

## 1. 迁移

C3 没有新增 Flyway 迁移，仍为 V13。

## 2. Prometheus 端点

```http
GET /actuator/prometheus
Accept: text/plain
```

需要文本 Accept；用 `Accept: application/json` 请求会触发内容协商失败（实际验证 500）。

## 3. 关键指标

```text
http_server_requests_seconds_count
http_server_requests_seconds_bucket
burger_menu_cache_requests_total{result="hit"}
burger_menu_cache_requests_total{result="miss"}
burger_menu_cache_requests_total{result="bypass"}
burger_menu_cache_requests_total{result="negative"}
burger_menu_cache_requests_total{result="stampede_wait"}
```

## 4. 统计端点

```http
GET /api/system/menu-cache
```

```json
{"hit":1,"miss":1,"bypass":0,"negative":0,"stampedeWait":0}
```

进程本地、重启归零。

## 5. 验证

```bash
curl -H 'Accept: text/plain' http://127.0.0.1:8080/actuator/prometheus \
  | grep -E 'burger_menu_cache_requests_total|http_server_requests_seconds_count'
```

## 6. 未建立

- Prometheus 服务发现/抓取配置；
- Grafana 面板；
- 告警规则；
- 多实例聚合；
- 指标长期存储。
