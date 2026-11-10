# Burger Order Lab

多门店汉堡点餐系统的分阶段实战工程。当前完成：

- **阶段 0：工程与联调**
- **阶段 A1：门店与菜单只读查询**
- **阶段 A2：固定账号登录、Session/CSRF、角色入口、本地购物车**
- **阶段 A3：下单、库存预占、幂等、订单详情**
- **阶段 A4：模拟支付、员工工作台、状态转换**
- **阶段 A5：顾客取消、超时取消、库存释放、支付/取消竞态**
- **阶段 B1：优惠券领取、下单锁券、支付核销、取消/超时释放**
- **阶段 B2：管理员商品/库存、乐观锁、库存流水与审计**
- **阶段 B3：管理员商品 keyset 游标分页、上架筛选与索引专项**
- **阶段 B4：幂等批量库存调整（全有或全无）**
- **阶段 B5：批量库存导入预览（dry-run）与逐行校验**
- **阶段 C1：门店菜单 Redis 缓存、TTL、写后失效与降级**
- **阶段 C2：single-flight、负缓存、TTL 抖动与缓存统计**
- **阶段 C3：Micrometer/Prometheus、缓存计数面板与性能基线**
- **阶段 D1：多阶段镜像、Nginx 反代、Compose app profile、CI 草案**
- **阶段 D2：一致性备份、临时库恢复、Redis 降级、安全检查**
- **阶段 D3：性能基线、发布检查、最终验收**

D3 的闭环：

```text
load-smoke: 菜单 200 req/20，状态 100 req/10
  -> P50/P95/P99、吞吐、错误、X-Cache
release-check: 4 服务健康 + 部署 + 安全 + 备份 + 测试 + 镜像
```

**工程计划 0–D3 已完成；练习未提交。**

## 页面

| 页面 | 地址 | 权限 |
| --- | --- | --- |
| 门店列表 | <http://127.0.0.1:5173/> | 公开 |
| 门店菜单 | <http://127.0.0.1:5173/stores/1> | 公开；加入购物车需 CUSTOMER |
| 购物车 / 领券 | <http://127.0.0.1:5173/cart> | CUSTOMER |
| 订单详情 | <http://127.0.0.1:5173/orders/1> | CUSTOMER，仅本人订单 |
| 登录 | <http://127.0.0.1:5173/login> | 公开 |
| 员工工作台 | <http://127.0.0.1:5173/staff> | STAFF，仅所属门店 |
| 管理员入口 | <http://127.0.0.1:5173/admin> | ADMIN |
| 商品与库存（分页/批量/预览） | <http://127.0.0.1:5173/admin/catalog> | ADMIN |
| 系统状态 | <http://127.0.0.1:5173/system> | 公开 |

## 固定示例账号

| 用户名 | 密码 | 角色 | 范围 |
| --- | --- | --- | --- |
| `demo_customer` | `Burger@123` | CUSTOMER | 选店、菜单、购物车、下单 |
| `demo_customer_two` | `Burger@123` | CUSTOMER | 用于验证订单归属隔离 |
| `demo_staff` | `Burger@123` | STAFF | 仅中央车站店 |
| `demo_admin` | `Burger@123` | ADMIN | 管理员基础入口 |

仅本地开发使用。密码以 BCrypt cost 12 散列保存在 `V5__seed_stage_a2_auth_users.sql`，数据库不存明文。

## 已实现

### 阶段 0

- Java 21 + Spring Boot 3.5.16 + Maven Enforcer
- MySQL 8.4.13、Flyway、liveness/readiness、统一错误体、traceId
- Vue 系统状态页、Vite 代理

### 阶段 A1

- `stores`、`products` 表与 3 家门店/18 个商品种子数据
- 门店列表、详情、按门店与上架状态过滤的菜单
- 价格 `DECIMAL(10,2)` / `BigDecimal` / JSON 字符串
- 门店 404、非法 ID 400、数据库不可用 503
- 菜单索引 `EXPLAIN` 验证
- 门店列表与菜单页、菜单请求竞态防护

### 阶段 A2

- `users`、`user_store_memberships` 表与 V5 固定账号种子
- BCrypt 密码校验、`BURGER_SESSION` HttpOnly Session
- CSRF Cookie + `X-XSRF-TOKEN` 请求头
- JSON 登录、当前用户、登出；Session fixation 防护
- 401/403/CSRF 失败统一 JSON 错误
- `/api/staff/workspace` 仅 STAFF，按 `user_id` 过滤所属门店
- `/api/admin/entry` 仅 ADMIN
- 顾客本地购物车：按门店整体保存、按用户隔离、localStorage 持久化
- 跨门店加入确认清空；本地小计仅展示
- 登录页、员工入口、管理员入口、购物车页
- Playwright 真实浏览器 smoke：登录、CSRF、加购、角色跳转、登出清空购物车

### 阶段 A3

- V6/V7：`orders`、`order_items`、`order_events`、`stock`、`stock_logs`、`idempotency_records`
- 服务端只接收商品 ID/数量，重新查询门店、商品归属、上架、价格和库存
- 条件预占：`available >= quantity`，同一 SQL 更新 available/reserved，检查影响行数
- 多商品按 productId 固定顺序更新，失败整笔回滚
- 订单、明细、库存、流水、事件、幂等记录同一事务
- Idempotency-Key 作用域 `(user_id, CREATE_ORDER, key)`，唯一约束防并发重复
- 同键同请求 200 重放；同键不同请求 409
- 订单详情仅本人可读；其他顾客统一 404
- 前端 checkout 持久化幂等键 + 购物车指纹，成功后清车并进入订单详情
- Playwright A3：购物车 → 下单 → 订单详情 → 刷新 → 未登录重定向

### 阶段 A4

- V8：stock_logs 增加 COMMIT 类型、订单工作台索引
- 顾客模拟支付：本人、WAIT_PAY、未过期；`WAIT_PAY → WAIT_MAKE`
- 支付提交 reserved，不再次扣 available；写 COMMIT 流水
- 支付并发只成功一次，只提交一次 reserved
- 员工工作台：所属门店的 WAIT_MAKE/MAKING/READY 活跃订单
- 开始制作、标记待取餐、完成订单；条件更新 + 事件同事务
- 顾客订单事件轨迹：CREATED/PAYMENT_SUCCEEDED/MAKING_STARTED/READY_FOR_PICKUP/COMPLETED
- 过期/重复/非法/越权转换明确 409/404/403
- Playwright A4：顾客支付 → 员工三段转换 → 顾客查看完成轨迹

### 阶段 A5

- V9：`(status, expires_at, id)` 超时扫描索引
- 顾客取消本人 WAIT_PAY 订单
- 取消释放 reserved→available，写 RELEASE 流水与 CANCELLED 事件
- 重复取消 409，不重复释放
- 超时扫描：初始 30s、间隔 60s、批大小 100，可配置
- 每单 REQUIRES_NEW，单笔失败不影响批次
- 支付/取消竞态只允许一个有效转换
- 顾客/员工/管理员取消权限与 404/403/409
- 前端取消按钮、CANCELLED 状态、事件轨迹
- Playwright A5：下单 → 取消 → 刷新仍为 CANCELLED

### 阶段 B1

- V10：`coupon_templates`、`user_coupons`、订单 `coupon_id` / `discount_amount`
- 固定金额券，单用户限领 1 张
- 领取总量条件自增；唯一键防同用户重复领取
- 最后一张券并发只有一个成功
- 同用户并发领取只有一个成功
- 下单锁券，同一张券并发只能锁一单
- 优惠金额服务端计算；商品小计/优惠/应付
- 支付核销 USED；取消/超时释放 UNUSED
- 购物车领券/选券/优惠预览；订单详情优惠
- Playwright B1：领券 → 选券 → 应付 59 → 下单 → 取消释放

### 阶段 B2

- V11：`products.version`、`stock_logs` 可空 order_id + operator、`admin_audit_logs`
- 管理员商品列表包含下架商品，展示商品/库存版本
- 改价/上下架使用商品版本乐观锁
- 库存调整使用库存版本乐观锁，禁止负可售
- ADJUST 库存流水（order_id NULL、operator、reason）
- 管理审计 JSON 前后值；与业务更新同事务
- 同版本并发库存调整只有一个成功
- `/admin/catalog` 管理台、改价/上下架/调库存对话框
- Playwright B2：管理员 +1 → 审计 → -1 恢复

### 阶段 B3

- V12：`(store_id,id)` 与 `(store_id,on_sale,id)` 索引
- 管理商品 keyset 分页：`afterId + id ASC`，不用 OFFSET
- `limit+1` 判断 hasMore，不额外 COUNT
- `nextCursor` / `hasMore` 契约
- 上架筛选
- 受控 100 行 EXPLAIN：不强制索引会 filesort；Mapper 显式 FORCE INDEX
- 前端首屏 5 条、加载更多、只看上架
- Playwright B3：5→7、筛选→6

### 阶段 B4

- V13：`admin_stock_batches` / `admin_stock_batch_lines`
- 批量库存 1–100 行，全有或全无
- `Idempotency-Key` + SHA-256 请求哈希，同键同体重放、异体 409
- 同键并发只应用一次；`SELECT ... FOR UPDATE` 解决 REPEATABLE READ 快照误判
- 每行 ADJUST 流水，整批一条 STOCK_BATCH 审计
- 管理台批量文本输入
- Playwright B4：批次 +1 → 审计 → -1 恢复

### 阶段 B5

- 批量预览端点 `POST /batch/preview`
- 逐行返回 valid/code/message/预计前后值
- 零调整、重复商品、库存不足、商品不存在逐行报告
- 预览只读：不写 stock/batch/log/audit
- 前端批量对话框“校验预览”
- 正式执行仍走 B4 幂等全有或全无
- Playwright B5：3 行预览，2 行错误，库存不变

### 刻意未实现

- CSV 文件上传
- dry-run/执行 diff
- 部分成功
- 异步批次/进度
- 批次历史/撤销
- 关键词搜索、随机跳页/总数
- 审计日志分页
- 商品新建/删除、门店营业/接单开关
- 审批/回滚
- 百分比券、封顶、舍入
- 多券叠加/互斥
- 券过期扫描、管理端发券/作废
- 退款、已支付订单取消
- payments 回调、对账
- 员工订单历史、统计
- 个人订单列表
- 分布式 single-flight、多实例失效/计数聚合、命中率看板、消息队列、WebSocket、统计
- 注册、JWT、多实例 Session

## 版本锁定

2026-11-09 检查环境与资料后锁定：

| 组件 | 版本 |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Spring Security | 6.5.11（Boot BOM 管理） |
| MyBatis-Plus | 3.5.18 |
| Flyway | 11.20.3（显式覆盖 Boot 默认 11.7.2） |
| MySQL / Connector-J | 8.4.13 / 9.7.0 |
| Node.js / pnpm | 24.18.0 / 12.11.2 |
| Vue / Vite | 3.5.45 / 8.3.6 |
| Vue Router / Pinia | 4.6.4 / 3.0.4 |
| Element Plus / Axios | 2.14.8 / 1.21.0 |
| Vitest / Playwright | 5.1.0 / 1.58.2 |
| TypeScript | 5.9.3 |

依据：

- [Spring Boot 3.5 System Requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [MyBatis-Plus 安装](https://baomidou.com/getting-started/install/)
- [Flyway MySQL driver](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
- [MySQL 8.4 Reference Manual](https://dev.mysql.com/doc/refman/8.4/en/)
- [Vite requirements](https://vite.dev/guide/)
- [Testcontainers MySQL](https://java.testcontainers.org/modules/databases/mysql/)

Redgate 的 Verified 列表未明确列出 MySQL 8.4，因此项目不宣称官方认证；该组合由真实 MySQL 8.4.13 集成测试验证。

## 目录职责

```text
backend/
  src/main/java/com/burgerlab/
    auth/             用户、密码校验、登录/登出/me、Session 安全
    staff/            员工门店 membership 与工作区入口
    admin/            管理员基础入口
    store/            门店状态与查询
    catalog/          商品与菜单查询
    platform/health/  liveness / readiness
    common/           错误体、traceId
  src/main/resources/db/migration/   V1–V5 唯一迁移来源
  src/test/java/      真实 MySQL、Session、CSRF、角色测试
frontend/
  src/api/            Axios（withCredentials + XSRF）、auth/catalog API
  src/stores/         auth、cart、门店、菜单、系统状态
  src/views/          选店、菜单、登录、购物车、员工、管理员、状态
  src/components/     共享导航、页脚
  e2e/                Playwright A2 smoke
deploy/compose.yaml   MySQL 8.4
scripts/              启停与验收
docs/                 架构、安全、API、数据库、学习、排错、证据
```

## 快速启动

前置：Java 21、Maven 3.9+、Node 22.12+、pnpm 10+、Docker。

```bash
cp .env.example .env
./scripts/db-up.sh

# macOS 示例
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./scripts/run-backend.sh

# 另开终端
cd frontend
pnpm install
pnpm dev
```

打开 <http://127.0.0.1:5173>。

可复现验收：

```bash
./scripts/verify-stage0.sh
./scripts/verify-stage-a1.sh
cd frontend && pnpm test:e2e
pnpm test:e2e:a3
```

`test:e2e`、`test:e2e:a3`、`test:e2e:a4`、`test:e2e:a5`、`test:e2e:b1`、`test:e2e:b2`、`test:e2e:b3`、`test:e2e:b4`、`test:e2e:b5`、`test:e2e:c1`、`test:e2e:c2`、`test:e2e:c3`、`test:e2e:d1` 需要本机 Google Chrome；不下载额外浏览器。

## 端口

| 项目 | 值 |
| --- | --- |
| 前端 | `127.0.0.1:5173` |
| 后端 | `127.0.0.1:8080` |
| MySQL | `127.0.0.1:13306` |
| Redis | `127.0.0.1:16379` |
| 容器前端 | `127.0.0.1:18081` |
| 容器后端 | `127.0.0.1:18080` |
| 数据库 | `burger_order` |

`.env` 不提交 Git；`.env.example` 只含本地占位值。

## Session 与 CSRF

1. 页面初始化调用 `GET /api/auth/csrf`，服务端设置 `XSRF-TOKEN` Cookie。
2. Axios 自动把 Cookie 放入 `X-XSRF-TOKEN` 请求头（`withCredentials` + XSRF 配置）。
3. 登录、登出等写请求同时带 Session Cookie 和 CSRF 头。
4. 登录成功创建 `BURGER_SESSION`：`HttpOnly; SameSite=Lax; Path=/`。
5. 本地开发 `Secure=false`；生产必须通过 `SESSION_COOKIE_SECURE=true` 或 HTTPS 配置开启。
6. CSRF Cookie 刻意不是 HttpOnly，因为浏览器端 Axios 需要读取；它只含 CSRF token，不是认证凭据。
7. 登出由 `SecurityContextLogoutHandler` 使 Session 失效并清除 SecurityContext。

前端路由守卫只做体验跳转；后端 Spring Security 与员工 membership 查询才是权限边界。

## API

| 方法 | 路径 | 访问 |
| --- | --- | --- |
| GET | `/api/stores` | 公开 |
| GET | `/api/stores/{storeId}` | 公开 |
| GET | `/api/stores/{storeId}/menu` | 公开；响应 `X-Cache: HIT/MISS/BYPASS` |
| GET | `/api/system/menu-cache` | 公开；缓存计数 |
| GET | `/actuator/prometheus` | 本地公开；Prometheus 文本指标 |
| GET | `/actuator/metrics/**` | 本地公开；Actuator 指标 |
| GET | `/api/auth/csrf` | 公开 |
| POST | `/api/auth/login` | 公开 + CSRF |
| GET | `/api/auth/me` | 已登录 |
| POST | `/api/auth/logout` | 已登录 + CSRF |
| GET | `/api/staff/workspace` | STAFF |
| GET | `/api/admin/entry` | ADMIN |
| GET | `/api/coupons/available` | CUSTOMER |
| GET | `/api/coupons/mine` | CUSTOMER |
| POST | `/api/coupons/{templateId}/claim` | CUSTOMER + CSRF |
| GET | `/api/admin/stores/{storeId}/products` | ADMIN，兼容全量 |
| GET | `/api/admin/stores/{storeId}/products/page` | ADMIN，keyset 分页 |
| PATCH | `/api/admin/products/{productId}` | ADMIN + CSRF |
| POST | `/api/admin/products/{productId}/stock-adjustments` | ADMIN + CSRF |
| POST | `/api/admin/stores/{storeId}/stock-adjustments/batch` | ADMIN + CSRF + Idempotency-Key |
| POST | `/api/admin/stores/{storeId}/stock-adjustments/batch/preview` | ADMIN + CSRF，只读 |
| GET | `/api/admin/audit-logs` | ADMIN |
| POST | `/api/orders` | CUSTOMER + CSRF + Idempotency-Key，可选 userCouponId |
| GET | `/api/orders/{orderId}` | CUSTOMER，仅本人 |
| POST | `/api/orders/{orderId}/pay` | CUSTOMER，仅本人 + CSRF |
| POST | `/api/orders/{orderId}/cancel` | CUSTOMER，仅本人 + CSRF |
| GET | `/api/orders/{orderId}/events` | CUSTOMER，仅本人 |
| GET | `/api/staff/orders?storeId=` | STAFF，仅所属门店 |
| POST | `/api/staff/orders/{id}/start` | STAFF，仅所属门店 + CSRF |
| POST | `/api/staff/orders/{id}/ready` | STAFF，仅所属门店 + CSRF |
| POST | `/api/staff/orders/{id}/complete` | STAFF，仅所属门店 + CSRF |
| GET | `/api/system/liveness` | 公开 |
| GET | `/api/system/status` | 公开 |
| GET | `/actuator/health/**` | 公开 |

错误语义：

- 未登录：401 `UNAUTHORIZED`
- 密码错误：401 `INVALID_CREDENTIALS`
- 角色不足：403 `FORBIDDEN`
- CSRF 缺失/不匹配：403 `CSRF_INVALID`
- 参数校验失败：400 `INVALID_REQUEST`
- 门店不存在：404 `STORE_NOT_FOUND`
- 数据库连接失败：503 `DATABASE_UNAVAILABLE`
- 库存不足：409 `STOCK_INSUFFICIENT`
- 幂等冲突：409 `IDEMPOTENCY_CONFLICT`
- 订单不存在/不属于自己：404 `ORDER_NOT_FOUND`
- 过期支付：409 `ORDER_EXPIRED`
- 非法/重复状态转换：409 `ORDER_STATUS_CONFLICT`
- 员工非所属门店：404
- 重复/非法取消：409 `ORDER_STATUS_CONFLICT`
- 重复领券：409 `COUPON_ALREADY_CLAIMED`
- 券领完：409 `COUPON_SOLD_OUT`
- 券不可用：409 `COUPON_NOT_USABLE`
- 未达券门槛：409 `COUPON_MIN_ORDER_NOT_MET`
- 管理版本冲突：409 `VERSION_CONFLICT`
- 库存调为负数：409 `STOCK_INSUFFICIENT`

契约：[B2 API](docs/api/stage-b2.md)、[完整 OpenAPI](docs/api/openapi.yaml)。

## 购物车边界

- 仅 CUSTOMER 可通过 UI 加购；员工/管理员不进入顾客购物车。
- 购物车按门店整体保存；跨门店加入会弹出确认，确认后清空旧购物车。
- localStorage key：`burger-order.cart.v1`。
- 按登录用户名绑定；登出或切换账号会清空。
- 价格保存为加入时快照，仅用于展示。
- 小计由整数分计算并格式化，但**不是成交依据**；A3 下单时服务端重新校验商品、价格、上架状态、门店状态与库存。
- A3 提交订单使用持久化 Idempotency-Key；成功后清空购物车并进入订单详情。
- 支付、状态转换、员工工作台、取消/超时释放、优惠券、管理端商品/库存已实现。
- 超时扫描配置：`app.order.timeout-scan.*`。

## 数据库

唯一迁移目录：

```text
backend/src/main/resources/db/migration/
  V1__create_system_metadata.sql
  V2__create_store_catalog.sql
  V3__seed_stage_a1_catalog.sql
  V4__create_auth_tables.sql
  V5__seed_stage_a2_auth_users.sql
  V6__create_order_inventory_tables.sql
  V7__seed_stage_a3_inventory.sql
  V8__add_payment_transition_support.sql
  V9__add_timeout_scan_index.sql
  V10__create_coupon_tables.sql
  V11__add_admin_inventory_management.sql
  V12__add_admin_product_pagination_indexes.sql
  V13__create_admin_stock_batches.sql
```

A3–B4 表：

- `orders` / `order_items` / `order_events`：订单、成交快照、事件轨迹
- `stock`：PK `(store_id, product_id)`，available/reserved/version
- `stock_logs`：预占前后值与订单关联
- `idempotency_records`：`(user_id, operation, idempotency_key)` 唯一

关键索引：

- `orders(order_no)` 唯一、`orders(user_id, created_at, id)`
- `idempotency_records` 作用域唯一
- `stock` 主键支持条件更新，`EXPLAIN` 使用 PRIMARY，rows=1
- `orders(store_id, status, created_at, id)` 用于员工工作台；实际计划仍 filesort，已如实记录
- `orders(status, expires_at, id)` 用于超时扫描；实际计划 `Using index`，无 filesort
- `coupon_templates` 主键条件更新；`user_coupons(template_id,user_id)` 唯一
- `products.version` / `stock.version` 管理端乐观锁
- `admin_audit_logs` 记录管理前后值；`stock_logs` 支持 order_id NULL 的 ADJUST
- `products(store_id,id)` / `(store_id,on_sale,id)` 用于 keyset 分页；受控 100 行下需 FORCE INDEX 才避免 filesort
- `admin_stock_batches(admin_user_id,idempotency_key)` 唯一；batch lines 记录前后值

B5 没有新增迁移；预览是只读查询。仍不创建 `payments` 表；没有百分比券/叠加。

## 测试

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
# Colima / 非默认 Docker context：
# export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
# export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
mvn -f backend/pom.xml test

cd frontend
pnpm typecheck
pnpm test
pnpm build
pnpm test:e2e
pnpm test:e2e:a3
pnpm test:e2e:a4
pnpm test:e2e:a5
pnpm test:e2e:b1
pnpm test:e2e:b2
pnpm test:e2e:b3
pnpm test:e2e:b4
pnpm test:e2e:b5
pnpm test:e2e:c1
pnpm test:e2e:c2
pnpm test:e2e:c3
pnpm test:e2e:d1
```

当前：

- 后端 **66** 个测试：5 缓存集成 + 2 缓存单元/并发 + 13 管理端 + 17 订单/优惠券 HTTP + 3 优惠券并发 + 4 订单并发 + 2 超时 + 7 Auth + 5 门店菜单 + 3 健康集成 + 3 健康单元 + 2 数据库故障映射
- 前端 **30** 个 Vitest：checkout 4、orderDetail 5、auth 4、cart 5、门店 2、菜单 3、状态 4、金额 3
- Playwright **13** 条路径：A2、A3、A4、A5、B1、B2、B3、B4、B5、C1、C2、C3、D1 容器

真实 MySQL 集成测试使用 Testcontainers；不会静默跳过。

## 健康检查

- `/api/system/liveness`：只证明进程能响应，不查数据库。
- `/api/system/status`：真实连接 + Flyway marker/history；故障时 503 完整快照。
- Actuator 只暴露 `/actuator/health/**`，其余 actuator 端点不再公开。

## 安全限制

A2 是本地教学实现，不是生产认证方案。尚未包含：

- 登录限流、失败锁定、MFA
- 记住我、Session 并发控制、集中式 Session
- 密码修改/重置、账号禁用管理
- CSRF 轮换策略的完整攻防测试
- 多实例共享 Session
- 生产 HTTPS、Secure Cookie、密钥管理、审计

详见 [A2 安全说明](docs/security/stage-a2.md)。

## 容器化部署（D1）

```bash
./scripts/deploy-local.sh
./scripts/verify-deploy.sh
```

- 前端：<http://127.0.0.1:18081>
- 后端：<http://127.0.0.1:18080>
- 镜像：`burger-order-lab-backend:latest`、`burger-order-lab-frontend:latest`
- CI：`.github/workflows/ci.yml`（本地只验证 YAML，未在远程 GitHub Actions 执行）

D1 边界：未推镜像仓库、未签名/扫描、未多架构、未 Kubernetes、未 TLS/回滚。

D2 运维脚本：

```bash
./scripts/backup-db.sh
./scripts/restore-drill.sh
./scripts/redis-fallback-check.sh
./scripts/security-check.sh
```

D2 边界：无 binlog/PITR、无备份加密/异地/轮转、无自动恢复、无 RPO/RTO 承诺、无生产 TLS/WAF/漏洞扫描。

D3 性能/验收：

```bash
./scripts/load-baseline.sh
./scripts/release-check.sh
```

本机容器基线：菜单 `p95 30.94ms`、状态 `p95 225.44ms`，0 errors；不是生产 SLA。

## 学习证据

工程进度与学习掌握度分开记录：[docs/progress.md](docs/progress.md)。

- [阶段 0 学习材料](docs/learning/stage-0.md)
- [阶段 A1 学习材料](docs/learning/stage-a1.md)
- [阶段 A2 学习材料](docs/learning/stage-a2.md)
- [阶段 A3 学习材料](docs/learning/stage-a3.md)
- [阶段 A4 学习材料](docs/learning/stage-a4.md)
- [阶段 A5 学习材料](docs/learning/stage-a5.md)
- [阶段 B1 学习材料](docs/learning/stage-b1.md)
- [阶段 B2 学习材料](docs/learning/stage-b2.md)
- [阶段 B3 学习材料](docs/learning/stage-b3.md)
- [阶段 B4 学习材料](docs/learning/stage-b4.md)
- [阶段 B5 学习材料](docs/learning/stage-b5.md)
- [阶段 C1 学习材料](docs/learning/stage-c1.md)
- [阶段 C2 学习材料](docs/learning/stage-c2.md)
- [阶段 C3 学习材料](docs/learning/stage-c3.md)
- [阶段 D1 学习材料](docs/learning/stage-d1.md)
- [阶段 D2 学习材料](docs/learning/stage-d2.md)
- [阶段 D3 学习材料](docs/learning/stage-d3.md)
- [阶段 D3 证据](docs/evidence/stage-d3.md)
- [阶段 D3 排错](docs/troubleshooting/stage-d3.md)

## 设计文档

- [阶段 0 架构](docs/architecture/stage-0.md)
- [阶段 A1 架构](docs/architecture/stage-a1.md)
- [阶段 A2 架构](docs/architecture/stage-a2.md)
- [阶段 A2 安全](docs/security/stage-a2.md)
- [阶段 A2 数据库](docs/database/stage-a2.md)
- [阶段 A2 API](docs/api/stage-a2.md)
- [阶段 A3 架构](docs/architecture/stage-a3.md)
- [阶段 A3 数据库](docs/database/stage-a3.md)
- [阶段 A3 API](docs/api/stage-a3.md)
- [阶段 A4 架构](docs/architecture/stage-a4.md)
- [阶段 A4 数据库](docs/database/stage-a4.md)
- [阶段 A4 API](docs/api/stage-a4.md)
- [阶段 A5 架构](docs/architecture/stage-a5.md)
- [阶段 A5 数据库](docs/database/stage-a5.md)
- [阶段 A5 API](docs/api/stage-a5.md)
- [阶段 B1 架构](docs/architecture/stage-b1.md)
- [阶段 B1 数据库](docs/database/stage-b1.md)
- [阶段 B1 API](docs/api/stage-b1.md)
- [阶段 B2 架构](docs/architecture/stage-b2.md)
- [阶段 B2 数据库](docs/database/stage-b2.md)
- [阶段 B2 API](docs/api/stage-b2.md)
- [阶段 B3 架构](docs/architecture/stage-b3.md)
- [阶段 B3 数据库](docs/database/stage-b3.md)
- [阶段 B3 API](docs/api/stage-b3.md)
- [阶段 B4 架构](docs/architecture/stage-b4.md)
- [阶段 B4 数据库](docs/database/stage-b4.md)
- [阶段 B4 API](docs/api/stage-b4.md)
- [阶段 B5 架构](docs/architecture/stage-b5.md)
- [阶段 B5 数据库](docs/database/stage-b5.md)
- [阶段 B5 API](docs/api/stage-b5.md)
- [阶段 C1 架构](docs/architecture/stage-c1.md)
- [阶段 C1 数据库](docs/database/stage-c1.md)
- [阶段 C1 API](docs/api/stage-c1.md)
- [阶段 C2 架构](docs/architecture/stage-c2.md)
- [阶段 C2 数据库](docs/database/stage-c2.md)
- [阶段 C2 API](docs/api/stage-c2.md)
- [阶段 C3 架构](docs/architecture/stage-c3.md)
- [阶段 C3 数据库](docs/database/stage-c3.md)
- [阶段 C3 API](docs/api/stage-c3.md)
- [阶段 D1 架构](docs/architecture/stage-d1.md)
- [阶段 D1 部署运行](docs/operations/stage-d1.md)
- [阶段 D2 架构](docs/architecture/stage-d2.md)
- [阶段 D2 运行手册](docs/operations/stage-d2.md)
- [阶段 D3 架构](docs/architecture/stage-d3.md)
- [阶段 D3 发布验收](docs/operations/stage-d3.md)
- [阶段 D3 证据](docs/evidence/stage-d3.md)

本项目是个人学习工程，不宣称生产经验。README 中的“已验证”只对应本机实际执行结果。
