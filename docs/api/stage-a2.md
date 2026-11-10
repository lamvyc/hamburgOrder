# 阶段 A2 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 认证方式

- Session Cookie：`BURGER_SESSION`，HttpOnly，SameSite=Lax。
- CSRF Cookie：`XSRF-TOKEN`，非 HttpOnly，供 Axios 读取。
- 写请求必须带 `X-XSRF-TOKEN`。
- 登录前先 `GET /api/auth/csrf` 取得 Cookie。
- 开发环境经 Vite 同源代理，浏览器不直接跨域访问 8080。

## 2. GET /api/auth/csrf

公开。成功 200：

```json
{
  "token": "d775b31a-a031-497e-99ae-32a40addfc1a",
  "headerName": "X-XSRF-TOKEN",
  "parameterName": "_csrf"
}
```

响应还设置：

```text
Set-Cookie: XSRF-TOKEN=...; Path=/; SameSite=Lax
```

## 3. POST /api/auth/login

公开，但 CSRF 校验开启。

请求：

```http
POST /api/auth/login
Content-Type: application/json
X-XSRF-TOKEN: <token>

{
  "username": "demo_customer",
  "password": "Burger@123"
}
```

成功 200：

```json
{
  "id": 1,
  "username": "demo_customer",
  "displayName": "演示顾客",
  "role": "CUSTOMER"
}
```

同时设置：

```text
Set-Cookie: BURGER_SESSION=...; Path=/; HttpOnly; SameSite=Lax
```

失败：

| HTTP | code | 原因 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 用户名/密码为空或格式不合法 |
| 401 | `INVALID_CREDENTIALS` | 账号密码错误 |
| 403 | `CSRF_INVALID` | 缺少/错误 CSRF |

## 4. GET /api/auth/me

需要 Session。成功 200 返回同样的用户对象。

未登录 401：

```json
{
  "code": "UNAUTHORIZED",
  "message": "请先登录",
  "traceId": "...",
  "timestamp": "...",
  "path": "/api/auth/me"
}
```

## 5. POST /api/auth/logout

需要 Session + CSRF。成功 204，无响应体。

- 服务端使 Session 失效；
- 清除 SecurityContext；
- 前端随后重新获取 CSRF Token；
- 前端清空本地购物车。

未登录 401，CSRF 错误 403。

## 6. GET /api/staff/workspace

需要 `ROLE_STAFF`。成功 200：

```json
{
  "stores": [
    {
      "id": 1,
      "code": "CENTRAL",
      "name": "中央车站店",
      "address": "静安区南京西路 88 号",
      "businessStatus": "OPEN",
      "acceptingOrders": true,
      "orderable": true
    }
  ]
}
```

- 只返回 `user_store_memberships` 中属于当前 principal 的门店；
- 顾客访问 403；
- 未登录 401；
- 管理员访问 403（管理员入口独立，不自动拥有员工数据范围）。

## 7. GET /api/admin/entry

需要 `ROLE_ADMIN`。成功 200：

```json
{
  "role": "ADMIN",
  "available": true,
  "message": "管理员基础入口已启用；门店、商品、优惠与统计管理将在后续阶段接入。",
  "checkedAt": "2026-11-09T11:32:16Z"
}
```

这里只证明角色入口，不返回任何管理数据。

## 8. 通用错误

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 请求体校验失败 |
| 400 | `INVALID_PARAMETER` | 路径参数格式错误 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 401 | `INVALID_CREDENTIALS` | 登录失败 |
| 403 | `FORBIDDEN` | 角色不足 |
| 403 | `CSRF_INVALID` | CSRF 缺失/不匹配 |
| 404 | `STORE_NOT_FOUND` | 门店不存在 |
| 500 | `INTERNAL_ERROR` | 未预期异常 |
| 503 | `DATABASE_UNAVAILABLE` | 数据库依赖不可用 |

所有错误响应带 `traceId`；不暴露 SQL、堆栈、密码或 Session 值。

## 9. 前端调用顺序

```text
GET  /api/auth/csrf       (设置 XSRF-TOKEN)
GET  /api/auth/me         (200 或 401)
POST /api/auth/login      (X-XSRF-TOKEN)
GET  /api/auth/me         (确认会话)
POST /api/auth/logout     (X-XSRF-TOKEN)
```

Axios 已设置：

```ts
withCredentials: true
xsrfCookieName: 'XSRF-TOKEN'
xsrfHeaderName: 'X-XSRF-TOKEN'
withXSRFToken: true
```

## 10. 购物车

A2 **没有**购物车后端 API，也没有购物车表。

购物车是：

- 浏览器 localStorage；
- Pinia 内存状态；
- 按登录顾客用户名绑定；
- 按门店整体保存；
- 跨门店加入必须显式清空旧购物车；
- 价格与库存不是权威数据。

A3 下单接口必须重新接收商品 ID/数量，并在服务端校验门店、商品归属、上架、价格与库存；不能信任本地购物车金额。
