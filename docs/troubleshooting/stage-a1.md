# 阶段 A1 排错入口

学习用的故障练习在 [阶段 A1 学习材料](../learning/stage-a1.md)，本页只记录可复现的排查入口。

## 1. 先分层

```text
MySQL -> Spring Boot -> /api/stores -> Vite proxy -> Vue
```

```bash
docker compose --env-file .env -f deploy/compose.yaml ps
curl -i http://127.0.0.1:8080/api/stores
curl -i http://127.0.0.1:5173/api/stores
```

不要先改 Vue 组件，直到你能指出失败发生在哪一层。

## 2. 门店列表失败

### 503 `DATABASE_UNAVAILABLE`

说明请求到了后端，但事务/连接无法建立。检查：

```bash
docker compose --env-file .env -f deploy/compose.yaml ps
docker compose --env-file .env -f deploy/compose.yaml logs mysql --tail=80
curl -i http://127.0.0.1:8080/api/system/status
```

`/api/stores` 的 503 是 `ApiErrorResponse`；`/api/system/status` 的 503 是完整状态快照。两者语义不同，不要用同一个前端分支硬套。

### 200 但 `items=[]`

- 查 `stores` 是否有数据；
- 检查 Flyway V3 是否执行；
- 检查是否连到了另一个数据库/端口。

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, code, name, business_status, accepting_orders FROM stores;"
```

## 3. 门店详情 404

```bash
curl -i http://127.0.0.1:8080/api/stores/999
```

- 404 + `STORE_NOT_FOUND`：门店确实不存在。
- 400 + `INVALID_PARAMETER`：路径 ID 不是整数。
- 500：先看后端 traceId 对应日志，不要用前端文案猜。

## 4. 菜单 404 与空菜单

```bash
curl -i http://127.0.0.1:8080/api/stores/1/menu
curl -i http://127.0.0.1:8080/api/stores/999/menu
```

- 门店不存在：404。
- 门店存在但没有在售商品：200 + `categories=[]`。
- 分类过滤后为空：应由分类过滤功能决定，不应复用“门店不存在”的错误。

## 5. 菜单里少了商品

按顺序检查：

1. `products.store_id` 是否正确；
2. `on_sale` 是否为 1；
3. API 是否返回该商品；
4. 浏览器 Network 中返回的 JSON 是否已经缺少它；
5. 最后才检查前端是否又过滤了一次。

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id, product_code, name, store_id, on_sale FROM products WHERE store_id = 1 ORDER BY id;"
```

## 6. 营业中但显示暂停接单

这是 A1 的刻意设计，不是必然的 bug：

```text
business_status=OPEN
accepting_orders=0
orderable=false
```

检查 `stores.accepting_orders`。如果运营希望接单，应修改状态数据，而不是前端把 `orderable` 写死为 true。A3 下单时仍会重新校验。

## 7. 快速切换门店后出现旧菜单

- 先看浏览器 Network：旧请求是否晚于新请求返回；
- 再看 `frontend/src/stores/storeMenu.ts` 的 `requestSequence`；
- 如果最新请求已经返回，旧响应不应再写入 `menu`；
- 清空旧菜单是刻意的，不要为了“避免闪动”保留上一个门店的数据。

## 8. 价格显示异常

- API 中 `price` 应为字符串，例如 `"32.00"`；
- 前端只通过 `formatPrice` 格式化；
- 如果 API 返回 number，先修服务端契约，不要在前端用 `toFixed` 掩盖边界问题；
- 购物车和成交金额不属于 A1。

## 9. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm typecheck && pnpm test && pnpm build
```

集成测试输出必须看到 MySQL 容器实际启动，且 `Skipped: 0`。
