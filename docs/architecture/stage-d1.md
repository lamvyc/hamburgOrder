# 阶段 D1 架构：CI/CD 与容器化交付

## 1. 目标

D1 把本地可运行工程变成可交付应用：

1. 后端多阶段 Docker 镜像。
2. 前端 Node 构建 + Nginx 运行镜像。
3. Nginx 反代 `/api` 与 `/actuator`，支持 SPA fallback。
4. Compose `app` profile 启动 backend/frontend。
5. 健康检查与依赖顺序。
6. 本地部署/验证脚本。
7. GitHub Actions：后端测试、前端构建、镜像构建。
8. 部署后真实浏览器登录/菜单验证。

## 2. 镜像

### Backend

```text
maven:3.9-eclipse-temurin-21
  -> dependency:go-offline
  -> package
eclipse-temurin:21-jre-jammy
  -> curl
  -> non-root app user
  -> /app/app.jar
```

- 不把 Maven/JDK 放进运行镜像；
- 非 root 运行；
- `MaxRAMPercentage=75`；
- 镜像大小约 581MB。

### Frontend

```text
node:24-alpine
  -> corepack pnpm 12.11.2
  -> pnpm install --frozen-lockfile
  -> pnpm build
nginx:1.27-alpine
  -> /usr/share/nginx/html
```

- 镜像约 76.6MB；
- 不包含 node_modules；
- 静态资源由 Nginx 提供。

## 3. Nginx

- `/api/` → `http://backend:8080`
- `/actuator/` → backend
- `/` → `try_files ... /index.html`
- 安全头：nosniff / DENY frame / referrer policy
- 同源代理避免生产 CORS；Session/CSRF Cookie 同源工作。

## 4. Compose profile

`deploy/compose.yaml`：

- mysql、redis 默认启动；
- backend/frontend 使用 `profiles: ["app"]`；
- backend 依赖 mysql/redis healthy；
- frontend 依赖 backend healthy；
- 端口：
  - backend `127.0.0.1:18080 -> 8080`
  - frontend `127.0.0.1:18081 -> 80`

日常开发仍用 host 的 8080/5173；容器部署用 18080/18081，互不覆盖。

## 5. 脚本

```text
scripts/deploy-local.sh
  -> compose build backend frontend
  -> compose --profile app up -d
  -> wait liveness + frontend

scripts/verify-deploy.sh
  -> backend liveness/status
  -> frontend HTML
  -> frontend /api/stores proxy
```

## 6. CI

`.github/workflows/ci.yml`：

- backend：Java 21 + `mvn -B test`（Testcontainers MySQL/Redis）；
- frontend：Node 22 + pnpm 12.11.2 + install/typecheck/test/build；
- images：直接 `docker build` 两个上下文。

本轮没有在远程 GitHub Actions 执行；只验证 YAML 结构、本地测试与镜像构建。

## 7. 边界

- 未推送到镜像仓库/GHCR；
- 未做镜像签名/扫描；
- 未做多架构构建（Docker buildx 插件未安装，classic builder）；
- 未做 Kubernetes/云部署；
- 未做蓝绿/回滚；
- Compose 仅本地交付演示。
