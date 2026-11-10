# 阶段 A2 学习材料

> 先提交你的分析或代码，再给答案。提示顺序：排查入口 → 局部线索 → 原因分析 → 完整解法。

## 1. 知识定位

层级：**L1/L2 — HTTP 认证、Cookie、CSRF、Spring Security、角色数据范围、前端状态持久化**。

本轮真实用到了：

- Session Cookie 与 HttpOnly/SameSite
- CSRF Cookie + 自定义请求头
- BCrypt 密码散列
- Spring Security FilterChain、AuthenticationManager、SecurityContextRepository
- 自定义 JSON 登录与会话 fixation 防护
- 401/403/CSRF 错误语义
- 员工 membership 数据范围
- Pinia + localStorage、按用户/门店隔离、跨店冲突
- Playwright 真实浏览器 smoke

订单事务、库存条件更新、幂等仍未进入代码。

## 2. 一句话本质

A2 的本质不是“能登录”，而是：**把认证凭据放在 HttpOnly Cookie，把 CSRF 作为写请求的独立门槛，把角色和员工门店范围放在后端校验，同时让本地购物车明确地不具备成交权威。**

## 3. 阅读问题

1. 为什么 A2 选择 Session 而不是 JWT？`BURGER_SESSION` 的 HttpOnly、SameSite 分别防什么？如果改成 `localStorage` 存 token，会引入什么新问题？
2. CSRF 的完整流程是什么？为什么 `XSRF-TOKEN` 必须允许 JavaScript 读取，而 Session Cookie 必须 HttpOnly？如果只依赖 `SameSite=Lax`、不加 CSRF Token，风险在哪里？
3. `AuthController.login` 为什么需要显式调用 `SessionAuthenticationStrategy` 与 `SecurityContextRepository.saveContext`？如果只 `SecurityContextHolder.setContext` 会发生什么？
4. 前端路由守卫为什么不能算权限校验？`/api/staff/workspace` 是如何保证 `demo_staff` 只能看到中央车站店的？如果改成接收前端 `storeId` 会有什么越权风险？
5. 本地购物车为什么按门店整体保存、按用户名绑定？“小计”为什么不能作为 A3 的成交金额？跨店加入时为什么必须显式确认清空？

## 4. 独立开发任务

**任务：新增 `GET /api/staff/stores/{storeId}/summary`，返回员工所属门店的商品分类数量。**

约束：

- 仅 STAFF 可访问。
- 必须从当前 principal 的 `user_id` 检查 membership；不能只比较前端传入的 storeId。
- 员工访问不属于自己的门店返回 403（或明确选择 404，但说明安全取舍）。
- 返回门店名、在售商品数量、分类数量。
- 补真实 MySQL 集成测试：staff 访问自己门店 200，访问其他门店 403，customer 403，未登录 401。
- 不修改现有 `/staff/workspace` 行为；不引入订单或管理写入。
- 前端员工入口可以展示一个“商品概况”，但不得把前端隐藏当权限。

## 5. 故障定位练习

**场景**

登录页面显示登录成功，但随后点击“加入购物车”或“退出”时，后端返回：

```text
HTTP 403
{"code":"CSRF_INVALID","message":"CSRF 校验失败，请刷新页面后重试"}
```

同时 `GET /api/auth/me` 返回 200。

请在不修改代码的前提下给出排查方案：

1. 你先检查哪些 Cookie 和请求头？分别证明什么？
2. 如何区分“Session 失效”“CSRF Cookie 缺失”“Axios 未发送 X-XSRF-TOKEN”“Vite 代理丢失 Cookie”？
3. 给出至少两条浏览器 DevTools 或 curl 命令，并说明预期。
4. 为什么 `SameSite=Lax` 不能替代 `X-XSRF-TOKEN` 校验？

先提交判断，不要先问答案。

## 6. 脱稿口述题

用 90 秒解释：

> “A2 的登录态如何保存？CSRF 怎么工作？为什么员工只能看自己的门店？本地购物车为什么不能作为成交依据？”

要求：

- 提到真实 Cookie 名、请求头名和至少一个后端类名；
- 明确区分 401、403 和 CSRF 403；
- 明确区分前端路由守卫与后端权限边界；
- 不照读代码。

## 7. 四级学习状态

| 能力 | 证据 | 当前 |
| --- | --- | --- |
| 功能已实现 | 代码、测试、E2E | 工程侧已完成 |
| 能解释机制 | 阅读问题回答 | 未评估 |
| 能独立实现相近功能 | 员工门店 summary 任务 | 未提交 |
| 能独立定位故障 | CSRF 403 排错 | 未提交 |

阶段 0、A1 的练习也仍未提交；不重复催确认。

## 8. 前置知识

- Cookie 的 HttpOnly / Secure / SameSite
- 同源与代理
- HTTP 401 与 403
- Spring Security FilterChain 的基本顺序
- BCrypt 与密码散列
- Pinia 状态与 localStorage
- 浏览器 Network/Application 面板

不要求本轮掌握：OAuth2、OIDC、JWT 签名轮换、分布式 Session、CAS。

## 9. 提交方式

提交阅读问题答案、独立任务代码与测试、CSRF 故障排查记录、口述提纲。我会先审查推理，再按提示层级补充。
