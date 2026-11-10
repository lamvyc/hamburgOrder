# 阶段 A2 安全说明

## 1. 范围

A2 引入：

- 固定示例账号与 BCrypt 密码散列
- HttpOnly Session Cookie
- CSRF Cookie + 请求头校验
- 登录、登出、当前用户
- 顾客 / 员工 / 管理员角色
- 员工按门店 membership 的数据范围
- 前端角色入口与本地购物车

不引入 JWT、注册、找回密码、登录限流、MFA、集中式 Session。

## 2. 为什么先用 Session 而不是 JWT

A2 是单体应用，登录态只需要一个浏览器会话：

- Session Cookie 可以设置 `HttpOnly`，脚本不能直接读取；
- 退出时服务端能使 Session 失效；
- 不需要在浏览器保存长期 Bearer Token；
- CSRF 是 Cookie 方案必须显式处理的问题，本轮正是学习点。

JWT 不是“更高级”的自动替代。它需要另行设计签名轮换、撤销、刷新、存储位置和泄露处置。多实例 Session 后续再作为独立扩展。

## 3. 密码

- 算法：BCrypt，cost 12。
- 数据库列：`users.password_hash VARCHAR(100)`。
- 种子账号统一密码：`Burger@123`（仅本地演示）。
- 明文不写数据库、不写日志、不出现在错误响应。

登录失败统一返回：

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "用户名或密码错误"
}
```

不区分“用户不存在”和“密码错误”，避免账号枚举。

## 4. Session Cookie

实际响应：

```text
Set-Cookie: BURGER_SESSION=...; Path=/; HttpOnly; SameSite=Lax
```

配置：

```yaml
server:
  servlet:
    session:
      timeout: 30m
      tracking-modes: cookie
      cookie:
        name: BURGER_SESSION
        http-only: true
        secure: ${SESSION_COOKIE_SECURE:false}
        same-site: lax
        path: /
```

- `HttpOnly`：JavaScript 不能读取会话 Cookie。
- `SameSite=Lax`：跨站 POST 默认不携带，降低 CSRF 面；服务端仍必须有 CSRF Token。
- `tracking-modes: cookie`：不接受 URL 重写里的 session id。
- `secure=false` 只用于本地 HTTP；生产必须 HTTPS + `Secure`。
- 30 分钟为本地默认，不是生产容量结论。

## 5. CSRF 流程

1. 前端调用 `GET /api/auth/csrf`。
2. 服务端设置：

```text
Set-Cookie: XSRF-TOKEN=...; Path=/; SameSite=Lax
```

3. 前端 Axios 配置：

```ts
withCredentials: true
xsrfCookieName: 'XSRF-TOKEN'
xsrfHeaderName: 'X-XSRF-TOKEN'
withXSRFToken: true
```

4. 登录、登出等写请求发送 `X-XSRF-TOKEN`。
5. Spring Security 比较 Cookie 与请求头；缺失或不匹配返回：

```json
{
  "code": "CSRF_INVALID",
  "message": "CSRF 校验失败，请刷新页面后重试"
}
```

Why CSRF Cookie 不是 HttpOnly：Axios 需要读取它并把值放进请求头。它只是 CSRF token，不是登录凭据；真正的登录凭据是 HttpOnly Session Cookie。攻击者能触发跨站请求，但不能读取同源 Cookie 的值并放入自定义头。

`SameSite=Lax` 是纵深防御，不替代 CSRF Token：

- 不同浏览器/旧客户端行为不完全一致；
- 同站子域、代理与部署拓扑可能变化；
- 安全边界应放在服务端，而不是只依赖 Cookie 属性。

## 6. Session fixation

自定义 JSON 登录不会自动执行 Spring Security 的表单登录策略。A2 显式做了三件事：

```java
sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
securityContextRepository.saveContext(context, request, response);
```

- `ChangeSessionIdAuthenticationStrategy` 在已有 Session 时更换 Session ID；
- 登录后显式保存 SecurityContext；
- 不依赖 `SecurityContextHolder` 的隐式保存。

如果只 `setContext` 不保存，后续请求会表现为“刚登录又未登录”；如果保存但不做 fixation 防护，预置 Session ID 的攻击面会扩大。

## 7. 401 / 403

| 场景 | HTTP | code |
| --- | --- | --- |
| 未登录访问 `/api/auth/me` | 401 | `UNAUTHORIZED` |
| 登录密码错误 | 401 | `INVALID_CREDENTIALS` |
| 顾客访问员工入口 | 403 | `FORBIDDEN` |
| 员工访问管理员入口 | 403 | `FORBIDDEN` |
| CSRF 缺失/错误 | 403 | `CSRF_INVALID` |
| 未登录访问员工接口 | 401 | `UNAUTHORIZED` |

安全过滤器在 MVC 之外，因此 A2 增加了 `SecurityErrorWriter`、`JsonAuthenticationEntryPoint`、`JsonAccessDeniedHandler`，保证错误仍是统一 JSON、带 `traceId`、不泄露内部细节。

## 8. 后端角色与数据范围

URL 级规则：

```text
/api/staff/**  -> ROLE_STAFF
/api/admin/**  -> ROLE_ADMIN
/api/auth/me   -> authenticated
/api/auth/logout -> authenticated
GET /api/stores/** -> public
```

员工门店查询额外按 `user_id` 过滤：

```sql
SELECT s.*
FROM user_store_memberships m
JOIN stores s ON s.id = m.store_id
WHERE m.user_id = #{userId}
```

不是客户端传 `storeId` 后直接信任。A4 的门店订单工作台必须继续沿用 principal 的 user id，而不是只接收前端门店参数。

管理员基础入口只返回入口状态，不伪造管理数据。

## 9. 前端路由守卫不是权限

`frontend/src/router/index.ts` 按角色跳转：

- CUSTOMER -> `/` / `/cart`
- STAFF -> `/staff`
- ADMIN -> `/admin`

它改善体验，但可以被绕过。真正的权限由后端 Spring Security 和 membership 查询校验。A2 的集成测试直接用真实 HTTP 验证 401/403，不依赖 Vue 路由。

## 10. 本地购物车

购物车没有后端服务和表，因此：

- 不是权限边界；
- 价格不是成交依据；
- 不向服务端提供任何“加购即锁库存”的保证；
- 按用户名绑定，登出/切换账号清空；
- 跨门店加入要求显式清空旧购物车。

A3 创建订单时必须重新校验门店、商品、上架状态、价格和库存。

## 11. 尚未解决的安全问题

- 登录失败限流、账号锁定、验证码、MFA
- 密码修改/重置与安全审计
- Session 并发登录控制
- 多实例 Session（Spring Session/Redis）
- CSRF token 轮换的完整攻击测试
- HTTPS、Secure Cookie、HSTS、生产密钥管理
- 管理员写接口的审计与二次确认
- 前端 XSS 对 CSRF Token 可读性的影响（需要 CSP/输出编码整体防护）

这些是后续阶段或生产化工作，不在 A2 伪装成“已完成”。
