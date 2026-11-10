# 阶段 A2 排错入口

学习用故障练习在 [阶段 A2 学习材料](../learning/stage-a2.md)。

## 1. 先区分 401、403 与 CSRF 403

| HTTP | code | 含义 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 没有有效 Session，先登录 |
| 401 | `INVALID_CREDENTIALS` | 账号或密码错误 |
| 403 | `FORBIDDEN` | 已登录，但角色/数据范围不足 |
| 403 | `CSRF_INVALID` | CSRF Cookie/Header 缺失或不匹配 |
| 400 | `INVALID_REQUEST` | 请求体校验失败 |

不要把所有 403 都当成“密码错误”。

## 2. 检查 Cookie 与请求头

浏览器 DevTools → Application → Cookies → `http://127.0.0.1:5173`：

- `BURGER_SESSION`：HttpOnly，登录后出现。
- `XSRF-TOKEN`：非 HttpOnly，页面初始化后出现。

Network 中登录/登出请求：

```text
Cookie: BURGER_SESSION=...; XSRF-TOKEN=...
X-XSRF-TOKEN: <与 XSRF-TOKEN 相同的值>
```

如果 `GET /api/auth/me` 200 但写请求 403 `CSRF_INVALID`：

1. `XSRF-TOKEN` 是否存在；
2. 请求头是否发送；
3. 两者值是否一致；
4. 是否经过了错误的域名/端口导致 Cookie 不在同一站点；
5. Vite 代理是否运行在 5173，前端是否直接请求 8080。

## 3. curl 复现 CSRF 流程

```bash
rm -f /tmp/a2-cookies.txt
curl -sS -c /tmp/a2-cookies.txt \
  http://127.0.0.1:5173/api/auth/csrf -o /tmp/a2-csrf.json
token=$(python3 -c 'import json; print(json.load(open("/tmp/a2-csrf.json"))["token"])')

curl -sS -b /tmp/a2-cookies.txt -c /tmp/a2-cookies.txt \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $token" \
  -d '{"username":"demo_customer","password":"Burger@123"}' \
  http://127.0.0.1:5173/api/auth/login

curl -sS -b /tmp/a2-cookies.txt http://127.0.0.1:5173/api/auth/me
```

不带 `X-XSRF-TOKEN` 的登录应得到 403 `CSRF_INVALID`。

## 4. 登录成功但刷新后变匿名

- Session Cookie 是否被浏览器保存；
- 是否在使用不同 host（`localhost` 与 `127.0.0.1` 不共享 host-only Cookie）；
- 应用是否重启导致内存 Session 丢失；
- `GET /api/auth/me` 的真实状态码。

当前 Session 存储在单个 Tomcat 内存中，重启后需要重新登录。多实例 Session 未实现。

## 5. 角色入口错误跳转

- CUSTOMER 访问 `/staff`：前端守卫跳回 `/`，后端 `/api/staff/workspace` 返回 403。
- STAFF 访问 `/admin`：前端跳回 `/staff`，后端 `/api/admin/entry` 返回 403。
- ADMIN 访问 `/staff`：前端跳回 `/admin`，后端员工接口 403。

如果绕过前端直接 curl，应以 401/403 为准。前端守卫不是权限边界。

## 6. 员工看不到所属门店

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT u.username, m.store_id FROM users u
      LEFT JOIN user_store_memberships m ON m.user_id = u.id
      WHERE u.username = 'demo_staff';"
```

工作区 SQL 只按当前 principal 的 `user_id` 查询，不接受前端传入的 store id。

## 7. 购物车异常

- 空购物车：检查 localStorage key `burger-order.cart.v1` 和当前用户名。
- 刷新后消失：检查 localStorage 是否被清理、是否在隐私模式。
- 登出后仍显示：检查 App 的 auth 初始化与 `cart.bindOwner(null)` 是否执行。
- 跨店加入：应弹出确认；确认后旧购物车清空。
- 员工/管理员看不到购物车入口：设计如此，只有 CUSTOMER 可加购。

本地小计不是服务端金额；不要用它调试 A3 订单金额。

## 8. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e
```

E2E 需要本机 Chrome，并假设前后端与 MySQL 已启动。失败时看 Playwright 输出的页面错误与截图。
