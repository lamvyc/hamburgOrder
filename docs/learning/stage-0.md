# 阶段 0 学习材料

> 这一页不提供练习答案。你先提交分析、代码或排查记录；我按“排查入口 → 局部线索 → 原因分析 → 完整解法”的顺序给提示。

## 1. 本轮知识定位

层级：**L1 — Java 必需基础、HTTP、分层、SQL、日志、简单事务与联调**。

这一轮只用到：

- HTTP 状态码、JSON、请求头/响应头
- Spring MVC 的 `@RestController`、`@GetMapping`
- Spring 的构造器注入
- MyBatis Mapper 注解与结果映射
- JDBC `Connection.isValid`
- Flyway 迁移与 `flyway_schema_history`
- SLF4J MDC 与 traceId
- Vue 3 `onMounted`、Pinia 状态、Axios 响应处理
- Maven / Vite / Docker Compose 的基本运行链路

事务、并发、库存条件更新、幂等、索引还没有进入真实代码，因此本轮不要求掌握这些。

## 2. 一句话本质

阶段 0 的本质不是“写一个健康检查接口”，而是：**把进程存活、数据库连接、schema 迁移这三个不同事实拆开验证，并让失败保持可观察、可追踪、不可伪装。**

## 3. 建议阅读顺序

1. `backend/src/main/resources/db/migration/V1__create_system_metadata.sql`
2. `backend/src/main/java/com/burgerlab/platform/health/SystemStatusMapper.java`
3. `backend/src/main/java/com/burgerlab/platform/health/PlatformHealthService.java`
4. `backend/src/main/java/com/burgerlab/platform/health/PlatformHealthController.java`
5. `backend/src/main/java/com/burgerlab/common/trace/TraceIdFilter.java`
6. `backend/src/main/java/com/burgerlab/common/error/GlobalExceptionHandler.java`
7. `frontend/src/api/system.ts`
8. `frontend/src/stores/systemStatus.ts`
9. `frontend/src/views/SystemStatusView.vue`
10. `frontend/vite.config.ts`

不要逐行翻译。每读一个文件，只回答：它负责什么事实？它不负责什么？

## 4. 阅读问题

1. `/api/system/liveness` 为什么不能查询数据库？请沿 `SystemStatusView.vue → api/system.ts → PlatformHealthController → PlatformHealthService` 指出实际方法链，并说明如果数据库挂掉，这个链路中哪些调用会失败、哪些不会。
2. `PlatformHealthService.status()` 分别捕获 `SQLException` 和 `DataAccessException`。这两个异常在当前代码里代表什么不同事实？为什么 `SCHEMA_NOT_READY` 分支可以声称 `connected=true`，证据是什么？
3. `TraceIdFilter` 为什么要用正则校验客户端传入的 `X-Trace-Id`？如果不校验，可能出现哪些具体问题？traceId 从请求头到日志、响应头、响应体分别经过哪些代码位置？
4. `/api/system/status` 在数据库故障时返回 HTTP 503，但响应体不是 `ApiErrorResponse`，而是完整的 `SystemStatusResponse`。这个契约区分解决了什么前端问题？`frontend/src/api/system.ts` 的 `validateStatus` 为什么要接受 503？
5. Flyway 的 `flyway_schema_history` 已经记录了迁移，`system_metadata.schema_ready` 是否冗余？请分别说出“只有 history”“只有 marker”“两者都有”三种情况下，健康检查能证明和不能证明什么。

## 5. 独立开发任务

**任务：新增 `GET /api/system/schema`，只读展示 `system_metadata` 表中的全部键值。**

约束：

- 新增 Controller 方法、Service 方法和 Mapper 查询，不复用 `/status` 的返回结构来偷懒。
- SQL 必须显式写出，结果按 `meta_key` 稳定排序。
- 数据库不可用时返回 503，响应体必须能让调用方区分“连接失败”和“查询失败”。
- 不允许把 SQL、堆栈、数据库地址返回给客户端。
- 后端至少补一个控制器测试；如果修改前端，必须补对应类型与测试。
- 不改动现有 `/liveness`、`/status` 的语义和已有测试。
- 完成后用 `curl -i` 或浏览器展示一次成功响应，并说明它经过了哪些层。

建议先写接口契约，再写实现。不要先写 Controller 再倒推 Service。

## 6. 故障定位练习

**场景**

应用和 MySQL 容器都在运行。打开页面点击“重新检测”后：

- 页面能加载；
- 浏览器请求 `/api/system/status` 约 3 秒后返回 HTTP 503；
- 响应体里 `application.status` 仍是 `UP`；
- `database.connected` 为 `false`；
- `docker compose ps` 显示 MySQL 容器是 `healthy`；
- 后端日志出现 `database_connectivity_probe_failed`，但没有输出数据库密码。

请在不修改代码的前提下，给出你的排查方案：

1. 你先看哪三个证据？分别证明什么？
2. 你如何判断这是“数据库没起来”“应用连错端口/库”“账号密码错误”“连接池配置错误”中的哪一种？
3. 如果容器 healthy 但应用连不上，`healthcheck` 本身能证明什么、不能证明什么？
4. 给出至少两条可执行命令，并说明每条命令的预期输出如何支持或推翻你的假设。

先提交你的判断，不要先问答案。

## 7. 脱稿口述题

用 90 秒解释：

> “为什么这个项目的健康检查不能只返回一个 200？liveness、readiness、业务状态接口分别回答什么问题？结合本项目的实际类名和一次数据库故障场景说明。”

要求：

- 不照读代码；
- 至少提到一个 HTTP 状态码选择；
- 至少提到一个真实类名或 SQL；
- 明确区分“功能实现了”和“我能独立设计并排查”。

## 8. 四级学习状态

| 能力 | 判定证据 | 当前状态 |
| --- | --- | --- |
| 功能已实现 | 代码存在且真实运行验证通过 | 工程侧已实现，等待你复跑 |
| 能解释机制 | 你能用自己的话回答阅读问题，指出调用链与取舍 | 未评估 |
| 能独立实现相近功能 | 你独立完成 `/api/system/schema` 或等价任务 | 未提交 |
| 能独立定位故障 | 你在不修改代码的前提下完成故障练习并验证 | 未提交 |

没有你的回答、代码或排查过程，上表不会标记为“已掌握”。

## 9. 前置知识与补课边界

如果以下概念有任何一个说不清，先补对应小块，不用展开成完整课程：

- HTTP 200 与 503 的语义差异
- JSON 对象与字段类型
- Spring 构造器注入的基本作用
- MyBatis 接口为什么不需要手写实现类
- SQL 标量子查询
- Flyway 迁移与 checksum
- 日志 MDC 为什么在线程池里必须清理
- Axios 的 `validateStatus` 与异常分支
- Pinia `ref` 在 store 中的读写方式

这些是理解本轮代码的门槛；事务隔离、索引结构、消息可靠性不是本轮门槛。

## 10. 提交方式

下轮开始时，直接提交：

- 阅读问题的答案（可以口语化）
- 独立任务代码或补丁
- 故障练习的排查记录与命令输出
- 口述题的提纲或录音转写

我会先审查，再给分层提示；不会在你提交前直接把答案写出来。
