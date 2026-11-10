# 阶段 0 API 契约

OpenAPI 文件：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 访问方式

开发环境推荐通过 Vite 代理访问：

```text
http://127.0.0.1:5173/api/system/status
```

后端直连：

```text
http://127.0.0.1:8080/api/system/status
```

阶段 0 没有认证、没有 CORS、没有 CSRF Token。前端 Axios 已设置 `withCredentials: true`，但这只是为 A2 的 Session 预留，不代表当前存在登录态。

## 2. 端点

| 方法 | 路径 | 说明 | 依赖数据库 |
| --- | --- | --- | --- |
| GET | `/api/system/liveness` | 进程存活，返回应用名、版本、Java、uptime | 否 |
| GET | `/api/system/status` | 连接校验 + 迁移标记 + Flyway 历史 | 是 |
| GET | `/actuator/health/liveness` | Actuator liveness group | 否 |
| GET | `/actuator/health/readiness` | Actuator readiness group，包含 DB | 是 |

所有响应带 `Cache-Control: no-store`。所有请求响应带 `X-Trace-Id`。

## 3. `GET /api/system/liveness`

成功：HTTP 200

```json
{
  "status": "UP",
  "service": "burger-order-api",
  "version": "0.1.0-SNAPSHOT",
  "checkedAt": "2026-11-09T10:00:00Z",
  "uptimeSeconds": 42
}
```

语义：

- 只要 JVM 与 Spring MVC 能处理请求，就返回 200。
- 不建立数据库连接；数据库故障不应影响此端点。
- 该端点失败通常意味着进程没起来、端口不通、网络不通或应用启动失败。

## 4. `GET /api/system/status`

### 200：应用和数据库均就绪

```json
{
  "status": "UP",
  "checkedAt": "2026-11-09T10:00:00Z",
  "traceId": "a1b2c3d4e5f6",
  "application": {
    "status": "UP",
    "name": "burger-order-api",
    "version": "0.1.0-SNAPSHOT",
    "javaVersion": "21.0.12",
    "uptimeSeconds": 42
  },
  "database": {
    "status": "UP",
    "connected": true,
    "product": "MySQL",
    "version": "8.4.13",
    "latencyMs": 5,
    "schemaReady": true,
    "flywayVersion": "1",
    "lastMigration": "create system metadata",
    "code": null,
    "message": null
  }
}
```

### 503：数据库不可用

```json
{
  "status": "DOWN",
  "checkedAt": "2026-11-09T10:00:00Z",
  "traceId": "a1b2c3d4e5f6",
  "application": {
    "status": "UP",
    "name": "burger-order-api",
    "version": "0.1.0-SNAPSHOT",
    "javaVersion": "21.0.12",
    "uptimeSeconds": 42
  },
  "database": {
    "status": "DOWN",
    "connected": false,
    "product": null,
    "version": null,
    "latencyMs": 3004,
    "schemaReady": false,
    "flywayVersion": null,
    "lastMigration": null,
    "code": "DATABASE_UNAVAILABLE",
    "message": "无法连接数据库"
  }
}
```

### 503：数据库可连，但迁移未就绪

```json
{
  "status": "DEGRADED",
  "checkedAt": "2026-11-09T10:00:00Z",
  "traceId": "a1b2c3d4e5f6",
  "application": {
    "status": "UP",
    "name": "burger-order-api",
    "version": "0.1.0-SNAPSHOT",
    "javaVersion": "21.0.12",
    "uptimeSeconds": 42
  },
  "database": {
    "status": "DEGRADED",
    "connected": true,
    "product": "MySQL",
    "version": "8.4.13",
    "latencyMs": 4,
    "schemaReady": false,
    "flywayVersion": null,
    "lastMigration": null,
    "code": "SCHEMA_NOT_READY",
    "message": "数据库可连接，但迁移状态读取失败"
  }
}
```

### 为什么 503 仍返回 `SystemStatusResponse`

页面需要区分三种情况：

1. 浏览器没收到任何响应：后端不可达。
2. 收到 503 + `connected=false`：后端活着，数据库连接失败。
3. 收到 503 + `connected=true` + `schemaReady=false`：数据库活着，迁移/SQL 层有问题。

如果 503 只返回一句“系统错误”，前端只能显示笼统失败，无法给出排错入口。

## 5. 未预期错误

HTTP 500：

```json
{
  "code": "INTERNAL_ERROR",
  "message": "服务暂时不可用，请稍后重试",
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-11-09T10:00:00Z",
  "path": "/api/system/liveness"
}
```

约束：

- 不返回 Java 堆栈、异常类名、SQL、数据库地址、账号或密码。
- 服务端日志按 traceId 记录完整异常，客户端只拿到可行动的信息。
- 404 使用 `NOT_FOUND`。

## 6. traceId

- 请求可携带 `X-Trace-Id`。
- 只接受 8–64 位 `[A-Za-z0-9._-]`；不合法则服务端生成新的 ID。
- 响应头始终带 `X-Trace-Id`。
- 状态响应和错误响应体也带 `traceId`，方便用户复制排错。
- 前端错误提示优先显示响应体中的 traceId，其次读取响应头。

## 7. 客户端状态处理

`frontend/src/api/system.ts` 使用：

```ts
validateStatus: (status) => status === 200 || status === 503
```

因此：

- 200 与 503 都进入正常数据分支；
- 只有网络错误、超时、500 等进入错误分支；
- 503 的 JSON 仍必须通过前端类型守卫，否则视为 `INVALID_RESPONSE`。

这与“所有非 2xx 都交给 Axios 抛异常”不同，是刻意让业务状态码可被页面解释。后续下单等接口也会遵循同一原则：失败不能被包装成 200，但客户端需要能区分失败类型。

## 8. 安全与后续变更

- 阶段 0 端点公开，仅用于本地开发。
- A2 引入 Spring Security 后，需要重新定义哪些健康端点公开、哪些需要认证。
- 后续新增字段必须同步更新 OpenAPI、前端类型和契约测试。
- 不允许在未更新契约的情况下悄悄改变字段含义。
