# 阶段 0 架构与取舍

## 1. 目标与边界

阶段 0 只回答四个工程问题：

1. 后端进程能否独立启动并响应 HTTP？
2. 数据库能否被应用真实访问？
3. Flyway 是否已经把最小 schema 应用成功，应用 SQL 是否能读到它？
4. Vue 页面能否通过代理调用后端，并把“进程状态”和“数据库状态”分别展示？

不实现登录、门店、菜单、订单、库存、Redis。这里没有业务模块空壳，避免后续用“目录存在”冒充能力存在。

## 2. 运行时拓扑

```text
浏览器 127.0.0.1:5173
  |
  |  /api, /actuator
  v
Vite dev server（代理，同源）
  |
  v
Spring Boot 127.0.0.1:8080
  |
  |  HikariCP
  v
MySQL 8.4.13 容器 127.0.0.1:13306
```

选择 Vite 代理而不是在后端开 CORS：

- 浏览器只看到 `127.0.0.1:5173` 一个 origin。
- 阶段 A2 引入 Session Cookie 时，不需要额外的跨域 Cookie 配置。
- 开发环境的代理边界也迫使调用方使用相对路径 `/api`，避免把 `localhost:8080` 写死在前端。

阶段 0 没有启用 Spring Security，也没有 CSRF Token；`withCredentials: true` 只是为后续 Session 预留。不能把“已经带 Cookie”误解为“已经实现认证”。

## 3. 实际调用链

### 3.1 页面加载

1. `frontend/src/views/SystemStatusView.vue` 在 `onMounted` 调用 `store.refresh()`。
2. `frontend/src/stores/systemStatus.ts` 调用 `fetchSystemStatus()`。
3. `frontend/src/api/system.ts` 通过 `frontend/src/api/http.ts` 的 Axios 实例请求 `GET /api/system/status`。
4. Vite 按 `frontend/vite.config.ts` 把 `/api` 代理到 `http://127.0.0.1:8080`。
5. `TraceIdFilter` 为请求生成或校验 `X-Trace-Id`，写入 MDC 与响应头。
6. `PlatformHealthController.status()` 调用 `PlatformHealthService.status()`。
7. Service 先从 HikariCP 获取连接并调用 `Connection.isValid(2)`。
8. Service 再通过 `SystemStatusMapper.selectProbe()` 执行显式 SQL，读取：
   - `system_metadata.schema_ready`
   - `flyway_schema_history` 最新成功迁移的版本与描述
9. Controller 根据聚合状态返回 HTTP 200 或 503。
10. Axios 把 200 与 503 都视为可解析的状态快照；其他状态进入错误处理。
11. Pinia 更新 `snapshot`、`backendReachable`、`error`，Vue 响应式更新两张状态卡。

### 3.2 关键文件

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 页面 | `frontend/src/views/SystemStatusView.vue` | 展示状态、触发刷新、格式化时间 |
| 状态 | `frontend/src/stores/systemStatus.ts` | 区分网络错误与降级快照 |
| API | `frontend/src/api/system.ts` | 校验 JSON 契约，接受 200/503 |
| HTTP | `frontend/src/api/http.ts` | 超时、统一错误映射、Cookie 预留 |
| 代理 | `frontend/vite.config.ts` | `/api`、`/actuator` 转发 |
| HTTP 层 | `PlatformHealthController` | 状态码与响应体 |
| 业务编排 | `PlatformHealthService` | 连接检查、迁移检查、状态聚合 |
| 数据访问 | `SystemStatusMapper` | 显式 SQL，不隐藏关键查询 |
| 迁移 | `V1__create_system_metadata.sql` | 唯一建表来源 |
| 横切 | `TraceIdFilter` | 请求关联、日志关联、响应头 |

## 4. 为什么把 liveness 和 readiness 分开

`GET /api/system/liveness` 不查询数据库。它只证明：

- JVM 进程仍在；
- Spring MVC 能处理请求；
- 返回应用名、构建版本、Java 版本和 uptime。

`GET /api/system/status` 和 `GET /actuator/health/readiness` 会访问数据库。它们回答的是“现在能否提供依赖数据库的服务”。

如果两者合并，会出现两种误导：

- 数据库挂了，健康检查失败，编排系统可能重启一个其实完全健康的进程；
- 进程死了，但缓存了一个旧的 `UP`，页面仍显示正常。

因此阶段 0 的验收必须同时出现：

- 停掉数据库后，liveness 仍为 200；
- 同一时刻 status/readiness 为 503；
- 恢复数据库后，status/readiness 回到 200。

这也是阶段 0 的故障练习入口。

## 5. 数据库探活为什么不是“固定返回成功”

Service 做了两层检查：

1. **连接层**：`DataSource.getConnection()` + `isValid(2)`。失败时 `connected=false`，状态 `DOWN`，错误码 `DATABASE_UNAVAILABLE`。
2. **应用 SQL 层**：`SystemStatusMapper.selectProbe()` 读取业务可见的迁移标记与 Flyway 历史。失败时 `connected=true`、`schemaReady=false`，状态 `DEGRADED`，错误码 `SCHEMA_NOT_READY`。

第二层不能省略。仅 `SELECT 1` 只能证明连接可用，不能证明：

- Flyway 迁移已经执行；
- 应用使用的表名、列名、字符集和 SQL 映射正确；
- 当前数据库确实是这个应用期望的 schema。

探活使用 MyBatis 而不是直接 JDBC 查所有内容，是为了同时验证 Mapper 映射链路；连接存活则由 DataSource 直接验证，避免把“表不存在”误报成“数据库断线”。

## 6. HTTP 语义

| 场景 | HTTP | 响应体 |
| --- | --- | --- |
| 应用存活 | 200 | `LivenessResponse` |
| 应用与数据库均正常 | 200 | `SystemStatusResponse(status=UP)` |
| 数据库连接失败 | 503 | 完整 `SystemStatusResponse(status=DOWN)` |
| 数据库可连但迁移不可读 | 503 | 完整 `SystemStatusResponse(status=DEGRADED)` |
| 未预期异常 | 500 | `ApiErrorResponse`，无堆栈与 SQL |

503 仍返回完整快照，是前端契约的一部分：页面需要知道“后端活着，但数据库故障”，不能只显示“请求失败”。500 则使用统一错误体，避免把内部细节泄露给浏览器。

`Cache-Control: no-store` 防止代理或浏览器缓存健康状态。

## 7. traceId

`TraceIdFilter`：

- 接受 8–64 位、仅 `[A-Za-z0-9._-]` 的 `X-Trace-Id`；
- 不合法时生成 32 位 UUID；
- 写入 SLF4J MDC，日志格式包含 `traceId=%X{traceId:-}`；
- 写入响应头与状态/错误响应体；
- 在 `finally` 中移除 MDC，避免线程池复用导致串号。

Why 要校验传入值：traceId 会进入日志与响应。未校验的换行、超长字符串可能造成日志注入或日志膨胀。跨服务链路可以继续传递合法 ID，但不能无条件信任客户端。

## 8. 时间、字符集与金额

阶段 0 没有金额，但先固定以下语义：

- 数据库容器时区 `+00:00`；
- JDBC URL 指定 `serverTimezone=UTC`；
- API 时间使用 `OffsetDateTime`，JSON 为带偏移量的 ISO-8601；
- 表字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。

阶段 A 引入金额时，Java 使用 `BigDecimal`，MySQL 使用 `DECIMAL`，并在服务端完成成交金额计算。

## 9. 本轮明确没有做的事

- 没有引入 Spring Security。A2 引入 Session 登录时再加，避免阶段 0 所有端点被默认拦截。
- 没有 CORS 配置。Vite 代理已经让开发环境同源。
- 没有创建 `users`、`stores`、`orders` 等表。还没有业务代码时建表只会制造错误的安全感。
- 没有创建 Redis、消息队列或 Outbox。
- 没有使用固定 JSON 冒充数据库验证。

## 10. 进入阶段 A 前要保留的约束

1. 迁移仍然只有一个来源：`backend/src/main/resources/db/migration/`。
2. 业务查询继续区分 MyBatis-Plus 常规访问与显式 SQL 关键路径。
3. 错误响应继续区分 HTTP 状态码，不把所有失败包装成 200。
4. 每个跨层功能都要能沿实际调用链解释，而不只是“页面能点”。
