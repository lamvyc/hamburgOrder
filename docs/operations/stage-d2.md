# 阶段 D2 运行手册

## 1. 备份

```bash
./scripts/backup-db.sh
```

输出：

```text
backup=/.../backups/burger_order-...sql.gz
bytes=...
sha256=...
```

检查：

```bash
ls -l backups/*.sql.gz
cat backups/*.sql.gz.meta
```

备份文件权限应为 `600`。

## 2. 恢复演练

```bash
./scripts/restore-drill.sh backups/burger_order-...sql.gz
```

默认取最新备份。

演练会：

1. 校验 gzip；
2. 创建 `burger_order_restore_check`；
3. 导入备份；
4. 对比 13 张关键表行数；
5. 校验 Flyway 最新版本；
6. 删除临时库。

不会修改 `burger_order`。

## 3. Redis 故障

```bash
./scripts/redis-fallback-check.sh
```

预期：

```text
redis fallback passed: status=200 X-Cache=BYPASS
```

脚本会停止并重新启动 Redis；容器 backend/frontend 不重启。

## 4. 安全检查

容器栈运行时：

```bash
./scripts/security-check.sh
```

检查项：

- Nginx nosniff / DENY / referrer；
- 未登录订单 API 401；
- backend uid 非 root；
- `.env`、`backups/` 被忽略。

## 5. 发布/回滚

当前：

- 镜像 tag 为 `latest`；
- 没有 registry；
- 回滚只能使用本地保留镜像/重新构建。

D2 未实现不可变 tag/自动回滚，见 D1 练习。

## 6. 事故处理顺序

1. 看 `docker compose ps` 与健康；
2. 看 backend/frontend 日志；
3. 看 `/api/system/status`；
4. 看 Redis/MySQL 健康；
5. 需要时执行备份（在破坏性操作前）；
6. 恢复演练只对临时库；不直接覆盖生产式库。

## 7. RPO/RTO

本项目未承诺生产 RPO/RTO：

- 无 binlog/PITR；
- 备份频率由人工脚本决定；
- 无异地副本；
- 无自动恢复。

不要把“演练成功”写成“生产可恢复”。
