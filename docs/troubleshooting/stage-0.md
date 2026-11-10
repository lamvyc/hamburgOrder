# 阶段 0 启动与联调排错手册

这份手册处理环境启动问题。学习用的故障练习在
[`docs/learning/stage-0.md`](../learning/stage-0.md)，不在这里提前给答案。

## 1. 先确认三层，而不是先改代码

```text
Docker 容器 -> Spring Boot 进程 -> Vite 页面
```

对应命令：

```bash
docker info
docker compose --env-file .env -f deploy/compose.yaml ps
curl -i http://127.0.0.1:8080/api/system/liveness
curl -i http://127.0.0.1:8080/api/system/status
curl -I http://127.0.0.1:5173
```

任何一层失败，都先修那一层，不要用前端报错去猜后端。

## 2. Docker / MySQL

### `docker info` 失败

Docker Desktop 或 Colima 没启动。先启动运行时，再执行：

```bash
./scripts/db-up.sh
```

### 端口 13306 被占用

```bash
lsof -nP -iTCP:13306 -sTCP:LISTEN
```

换端口：

```bash
MYSQL_PORT=13307 ./scripts/db-up.sh
```

同时修改 `.env` 中的 `MYSQL_PORT`，并重启后端。前后端必须指向同一个端口。

### 容器一直 unhealthy

```bash
docker compose --env-file .env -f deploy/compose.yaml logs mysql --tail=120
```

常见原因：

- 数据卷中的旧密码与新 `.env` 不一致；
- 首次初始化尚未完成；
- 机器内存不足；
- 端口映射冲突。

本地一次性实验库可以重建卷，但会丢失数据：

```bash
docker compose --env-file .env -f deploy/compose.yaml down -v
```

不要在存放真实数据的库上执行。

### 容器 healthy，但应用仍连不上

`healthcheck` 只能说明容器内的 `mysqladmin ping` 成功，不能证明：

- 宿主映射端口正确；
- 应用使用了正确的主机、端口、数据库名；
- 应用账号有权限；
- Hikari 配置没有超时；
- 网络策略允许连接。

逐一核对：

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml port mysql 3306
docker compose --env-file .env -f deploy/compose.yaml exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT DATABASE(), CURRENT_USER(), VERSION();"
```

## 3. 后端

### Maven 使用了错误的 Java

```bash
mvn -version
```

本项目的编译目标是 Java 21。若 Maven 显示 Java 26 等其它运行时，先设置：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
mvn -version
```

`scripts/run-backend.sh` 会在 macOS 上尝试选择 Java 21，但不会覆盖你显式设置的 `JAVA_HOME`。Maven Enforcer 会直接拒绝非 Java 21 运行时，避免在 Java 26 下“碰巧构建成功”。

### 启动报 Flyway 连接失败

检查：

```bash
curl -i http://127.0.0.1:8080/api/system/liveness
```

如果应用根本没起来，先看启动日志中 Flyway 的连接目标。Flyway 在应用启动阶段执行，数据库不可用会阻止启动，这是有意的 fail-fast。

### Flyway checksum mismatch

不要修改已经执行过的迁移。新增迁移修复 schema。查看历史：

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T mysql \
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  -e "SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history;"
```

本地一次性实验环境可重建 volume；共享环境必须走新的迁移。

### `/api/system/status` 返回 503

先看响应体，不要只看状态码：

- `database.connected=false`：连接层失败；
- `database.connected=true` 且 `schemaReady=false`：连接成功，但迁移/SQL 层失败；
- `code=SCHEMA_NOT_READY`：检查 `system_metadata`、`flyway_schema_history` 与迁移是否执行。

再去后端日志按响应中的 traceId 搜索：

```bash
# 假设 traceId=abc123...
# 在运行后端的终端里搜索 traceId=abc123
```

## 4. 前端

### 页面能打开，但显示后端不可达

```bash
curl -i http://127.0.0.1:5173/api/system/liveness
```

- 503/200 有响应：后端与代理至少通了一层；
- 502/504：Vite 代理目标不可达；
- 连接被拒绝：Vite 没启动或端口不对。

检查 `frontend/.env.local` 是否覆盖了 `VITE_API_PROXY_TARGET`。默认目标是
`http://127.0.0.1:8080`。

### 页面显示 200 但数据库未知

检查 `frontend/src/api/system.ts` 的类型守卫与 `SystemStatusResponse`。如果字段改名而前端类型没同步，会进入 `INVALID_RESPONSE`，而不是“数据库未知”。

### Element Plus 样式缺失

本项目按需引入 `ElAlert`、`ElButton`、`ElTag`，并在 `src/main.ts` 显式引入对应 CSS。新增组件时必须同时引入其组件与样式，不能假设全局注册还在。

## 5. 测试

### Testcontainers 无法连接 Docker

测试输出出现 “Could not find a valid Docker environment” 时，测试会失败，项目不会把它自动跳过。先确认：

```bash
docker info
```

若使用的是 Colima 等非默认 Docker context，需要让 Testcontainers 找到 socket。例如当前开发机：

```bash
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test
```

具体路径以 `docker context inspect` 为准。测试日志中必须看到实际启动的 MySQL 容器，并且 `Skipped: 0`；如果 Docker 不可用，应修复环境后重跑，而不是解释成“通过”。

### 前端测试通过但构建失败

分别执行：

```bash
pnpm typecheck
pnpm test
pnpm build
```

Vitest 通过不代表 `vue-tsc` 通过，反之亦然。

## 6. 提交排错记录时至少包含

- 操作系统、Java、Node、Docker 版本
- 实际执行的命令
- 完整错误行（隐藏密码）
- traceId
- 你最初假设、验证结果、修正后的假设
- 最终是否恢复，以及恢复证据

不要只贴“报错了”或一张截断的截图。
