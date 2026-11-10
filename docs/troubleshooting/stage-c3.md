# 阶段 C3 排错入口

学习故障练习在 [阶段 C3 学习材料](../learning/stage-c3.md)。

## 1. /actuator/prometheus 404

- 依赖是否有 `micrometer-registry-prometheus`；
- `management.endpoints.web.exposure.include` 是否包含 `prometheus`；
- `management.endpoint.prometheus.access` 是否可访问；
- 是否误设了与 `access` 互斥的 `enabled`；
- 安全规则是否 permit。

C3 实际踩到：`management.endpoint.prometheus.enabled` 与 `access` 同时配置导致上下文启动失败。

## 2. /actuator/prometheus 500

C3 实际踩到：请求带 `Accept: application/json`，Prometheus 只提供文本格式，内容协商失败。

使用：

```bash
curl -H 'Accept: text/plain' http://127.0.0.1:8080/actuator/prometheus
```

## 3. 指标不存在

- 计数器是否注册到 `MeterRegistry`；
- 标签值是否拼错（hit/miss/bypass/negative/stampede_wait）；
- 进程是否重启（计数归零）；
- 是否命中缓存（hit 需要第二次请求）；
- Prometheus 名称会把 `.` 转 `_`、Counter 加 `_total`。

## 4. 系统页显示 0

- 是否先请求过菜单；
- 缓存是否启用；
- Redis 是否可用；
- 计数是进程本地，重启归零。

## 5. 高基数

不要用 storeId/userId/traceId 作为指标标签；使用受控枚举或聚合桶。

## 6. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend && pnpm test:e2e:c3
```
