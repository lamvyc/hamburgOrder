# 阶段 A2 架构与取舍

## 1. 目标

A2 完成一个可运行闭环：

1. 固定账号用 JSON 登录。
2. Spring Security 创建服务端 Session。
3. 前端通过 CSRF Cookie/Header 完成写请求。
4. 顾客、员工、管理员进入不同入口。
5. 后端独立校验角色与员工门店范围。
6. 顾客购物车在 Pinia + localStorage 中按门店和用户保存。

不创建订单、库存、支付表和接口。

## 2. 认证链路

```text
GET /api/auth/csrf
  -> CookieCsrfTokenRepository 设置 XSRF-TOKEN
POST /api/auth/login
  -> AuthController
  -> AuthenticationManager
  -> DatabaseUserDetailsService
  -> BCryptPasswordEncoder(12)
  -> AppUserPrincipal
  -> ChangeSessionIdAuthenticationStrategy
  -> HttpSessionSecurityContextRepository.saveContext
  -> BURGER_SESSION (HttpOnly, SameSite=Lax)
GET /api/auth/me
  -> SecurityContextHolderFilter 从 Session 恢复
  -> AuthController.me
```

关键文件：

| 层 | 文件 |
| --- | --- |
| 安全配置 | `backend/src/main/java/com/burgerlab/auth/security/SecurityConfig.java` |
| 登录/登出 | `backend/src/main/java/com/burgerlab/auth/AuthController.java` |
| 用户加载 | `backend/src/main/java/com/burgerlab/auth/DatabaseUserDetailsService.java` |
| principal | `backend/src/main/java/com/burgerlab/auth/AppUserPrincipal.java` |
| 安全错误 | `JsonAuthenticationEntryPoint`、`JsonAccessDeniedHandler`、`SecurityErrorWriter` |
| 员工范围 | `backend/src/main/java/com/burgerlab/staff/UserStoreMembershipMapper.java` |
| 前端 Session | `frontend/src/stores/auth.ts` |
| 前端购物车 | `frontend/src/stores/cart.ts` |

## 3. 为什么自定义 JSON 登录

Spring Security 默认 `formLogin` 接收 `application/x-www-form-urlencoded`，而 SPA 使用 JSON。A2 使用自定义 Controller，但没有绕过安全机制：

- 通过 `AuthenticationManager` 校验 BCrypt 密码；
- 显式调用 `SessionAuthenticationStrategy` 做 fixation 防护；
- 显式保存 SecurityContext；
- CSRF 仍由 Spring Security Filter 校验。

如果只写“查询用户名密码并返回 200”，会绕过认证与 Session 持久化，属于错误示范。

## 4. CSRF 设计

- `CookieCsrfTokenRepository.withHttpOnlyFalse()`
- Cookie：`XSRF-TOKEN`，Path `/`，SameSite Lax
- Header：`X-XSRF-TOKEN`
- Axios：`withCredentials` + `withXSRFToken`
- Vite 代理保证同源，避免跨域 Cookie 复杂度

CSRF Cookie 不是认证凭据。Session Cookie 是 HttpOnly；攻击脚本不能读取它。CSRF Token 必须可被同源前端读取，才能放进自定义请求头。

## 5. 角色与数据范围

URL 规则：

```text
GET  /api/stores/**        public
GET  /api/auth/csrf        public
POST /api/auth/login       public + CSRF
GET  /api/auth/me          authenticated
POST /api/auth/logout      authenticated + CSRF
GET  /api/staff/**         ROLE_STAFF
GET  /api/admin/**         ROLE_ADMIN
```

员工数据范围不靠前端隐藏菜单：

```sql
WHERE m.user_id = #{principal.id}
```

`demo_staff` 只关联门店 1；集成测试断言工作区只返回中央车站店。

管理员基础入口只返回入口信息，不返回虚构统计。

## 6. 前端路由守卫

`router.beforeEach`：

1. 首次导航调用 `auth.initialize()`：CSRF → `/me`。
2. `requiresAuth` 且未登录 → `/login?redirect=...`。
3. 角色不足 → `homeForRole(role)`。
4. 已登录访问 `/login` → 角色首页。

这只是体验层。后端对 `/api/staff/**`、`/api/admin/**` 的 401/403 才是权限边界。

## 7. 购物车设计

`frontend/src/stores/cart.ts`：

- 单门店购物车，不混合跨店商品。
- 跨店加购抛出 `CartStoreMismatchError`，由菜单页弹确认；确认后 `replaceWith`。
- localStorage key 固定：`burger-order.cart.v1`。
- 状态含 `ownerUsername`，登录用户变化时由 App 监听清理。
- 小计按整数分计算，仅展示。
- A2 无结算按钮。

Why 不在后端建购物车：

- 本轮目标是会话与角色；
- 本地购物车没有跨设备需求；
- 提前建表会诱导错误地信任本地价格；
- A3 下单服务端重新校验即可。

## 8. 失败语义

| 场景 | HTTP |
| --- | --- |
| 未登录 | 401 `UNAUTHORIZED` |
| 密码错误 | 401 `INVALID_CREDENTIALS` |
| 角色不足 | 403 `FORBIDDEN` |
| CSRF 失败 | 403 `CSRF_INVALID` |
| 请求体验证失败 | 400 `INVALID_REQUEST` |
| 数据库不可用 | 503 `DATABASE_UNAVAILABLE` |

安全过滤器错误由 `SecurityErrorWriter` 写统一 `ApiErrorResponse`，不经过 MVC 的 `@RestControllerAdvice`。

## 9. 已验证与未验证

已验证：

- 真实 MySQL + BCrypt 登录；
- Session Cookie、CSRF、登出失效；
- 401/403/CSRF 错误体；
- 员工 membership 数据范围；
- 前端真实浏览器登录/加购/角色跳转/登出清车。

未验证：

- 登录限流、锁定、MFA；
- 集中式 Session、多实例；
- HTTPS/Secure Cookie 生产部署；
- CSRF 攻击面完整测试；
- XSS/CSP 对 token 可读性的影响。

详细安全边界见 [安全说明](../security/stage-a2.md)。
