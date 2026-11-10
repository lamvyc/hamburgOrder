# 阶段 D2 排错入口

学习故障练习在 [阶段 D2 学习材料](../learning/stage-d2.md)。

## 1. 备份失败

- `.env` 是否存在；
- MYSQL_ROOT_PASSWORD 是否正确；
- mysql 容器是否 healthy；
- mysqldump 参数是否被当前 MySQL 支持；
- 脚本 `pipefail` 是否让 gzip 半成品保留（应清理）。

检查：

```bash
docker compose ... exec -T mysql mysqldump --version
ls -l backups/
```

## 2. 恢复外键错误

- 目标库是否为空；
- dump 是否包含 `FOREIGN_KEY_CHECKS=0`；
- 是否中途失败留下半成品；
- schema 版本是否匹配；
- 字符集/collation 是否一致。

## 3. 行数不一致

- 备份后源库是否继续写入（演练期间）；
- 是否对比了不同时间点；
- 是否漏了某张表；
- 是否使用了近似 `table_rows` 而不是 COUNT。

## 4. Redis 降级失败

```bash
curl -D - http://127.0.0.1:8080/api/stores/1/menu
```

- X-Cache 应为 BYPASS；
- 缓存是否 disabled；
- 后端是否仍连旧 Redis；
- 超时是否过长；
- Redis 是否真的停止。

## 5. 安全检查失败

- 容器栈是否运行在 18080/18081；
- Nginx headers 是否被覆盖；
- backend 是否 root（Dockerfile USER）；
- `.env`/`backups` 是否被 gitignore；
- 未登录订单 API 是否 401。

## 6. 脚本

```bash
bash -n scripts/backup-db.sh scripts/restore-drill.sh \
  scripts/redis-fallback-check.sh scripts/security-check.sh
```
