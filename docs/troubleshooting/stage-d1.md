# 阶段 D1 排错入口

学习故障练习在 [阶段 D1 学习材料](../learning/stage-d1.md)。

## 1. frontend 502

- `nginx.conf` 的 `proxy_pass http://backend:8080`；
- Compose service 是否叫 backend；
- backend 是否 healthy；
- 端口是容器 8080，不是 host 18080；
- `proxy_pass` 是否错误地丢了 `/api` 前缀。

```bash
docker compose ... logs --tail=100 frontend backend
docker compose ... exec frontend wget -qO- http://backend:8080/api/system/liveness
```

## 2. 后端容器不健康

```bash
docker compose ... ps
docker compose ... logs --tail=200 backend
```

常见：

- MySQL 未 healthy；
- DB_URL/用户/密码错误；
- Flyway 迁移失败；
- Redis 未 healthy；
- curl 未安装（镜像已安装）。

## 3. 登录/CSRF 异常

- 前后端必须同源（Nginx 反代）；
- Session Cookie path `/`；
- `SESSION_COOKIE_SECURE` 本地必须是 false；
- Nginx 是否转发 Cookie（默认转发）；
- 是否直接访问 18080 与 18081 混用 Origin。

## 4. 端口冲突

- host 开发：8080/5173；
- 容器：18080/18081；
- 如占用，改 `APP_BACKEND_PORT`/`APP_FRONTEND_PORT`。

## 5. CI 镜像 job 失败

- 不要依赖 `.env` 的必填变量做 `compose build`；
- 直接 `docker build` backend/frontend；
- 检查 Dockerfile 上下文；
- Testcontainers 需要 runner Docker。

## 6. 镜像构建缓存

- `dependency:go-offline` 层先复制 pom；
- 前端先复制 lockfile 再 install；
- 代码变化后重建；
- `--no-cache` 仅用于排查。

## 7. 命令

```bash
./scripts/deploy-local.sh
./scripts/verify-deploy.sh
E2E_BASE_URL=http://127.0.0.1:18081 pnpm test:e2e:d1
```
