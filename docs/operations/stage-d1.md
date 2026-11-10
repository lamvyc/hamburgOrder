# 阶段 D1 部署运行说明

## 1. 前置

- Docker/Compose；
- `.env`（从 `.env.example` 复制）；
- 端口 18080/18081 未被占用。

## 2. 部署

```bash
./scripts/deploy-local.sh
./scripts/verify-deploy.sh
```

或：

```bash
docker compose --env-file .env -f deploy/compose.yaml --profile app build backend frontend
docker compose --env-file .env -f deploy/compose.yaml --profile app up -d
```

访问：

- 前端：<http://127.0.0.1:18081>
- 后端：<http://127.0.0.1:18080>
- Prometheus：<http://127.0.0.1:18080/actuator/prometheus>

## 3. 数据

Compose 复用：

- `burger_order_mysql_data` volume；
- Redis 容器（缓存可丢）；
- MySQL migration V13 在容器启动时自动校验。

容器 backend 连接：

```text
DB_URL=jdbc:mysql://mysql:3306/burger_order...
REDIS_HOST=redis
```

## 4. 健康检查

```bash
docker compose --env-file .env -f deploy/compose.yaml --profile app ps
curl -fsS http://127.0.0.1:18080/api/system/liveness
curl -fsS http://127.0.0.1:18081/api/stores
```

## 5. 停止

```bash
docker compose --env-file .env -f deploy/compose.yaml --profile app stop backend frontend
```

不删除 MySQL volume。

## 6. 升级

```bash
git pull
./scripts/deploy-local.sh
```

未实现滚动升级/回滚；停止期间不可用。

## 7. 限制

- 单机 Compose，不是生产编排；
- 无镜像仓库/签名/扫描；
- 无多实例 Session/Redis 集群；
- 无外部 TLS 终止（本地 HTTP）。
