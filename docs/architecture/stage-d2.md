# 阶段 D2 架构：备份、故障演练与安全收口

## 1. 目标

D2 不增加业务功能，验证交付后的运行保障：

1. MySQL 一致性备份。
2. 恢复到临时库并逐表对账。
3. Flyway 版本校验。
4. Redis 故障时菜单降级。
5. 容器安全头、未登录 401、非 root、忽略文件检查。
6. 运行手册与边界。

## 2. 备份

`scripts/backup-db.sh`：

```text
mysqldump --single-transaction --routines --triggers
         --no-tablespaces --set-gtid-purged=OFF
  | gzip -9
  -> backups/{db}-{UTC timestamp}.sql.gz
  -> .meta (createdAt/db/file/bytes/sha256)
  -> chmod 600
```

- `--single-transaction` 给 InnoDB 一致性快照，不阻塞写入；
- 不备份 binlog，因此不是 PITR；
- 备份文件本地保存、gitignore；
- 无自动轮转/加密/异地存储。

## 3. 恢复演练

`scripts/restore-drill.sh [backup]`：

```text
gunzip -t
创建 burger_order_restore_check
导入 dump
逐表 COUNT 对比源库
校验 flyway_schema_history 最大版本
DROP 临时库
```

**只恢复到临时库**，不覆盖开发/容器数据库。

## 4. Redis 故障演练

`scripts/redis-fallback-check.sh`：

```text
stop redis
GET /api/stores/1/menu -> 200 + X-Cache: BYPASS
start redis
wait healthy
```

验证 C1 降级路径，而不是只测正常缓存。

## 5. 安全检查

`scripts/security-check.sh`：

- Nginx 安全头；
- 未登录 `/api/orders` = 401；
- backend 容器 uid != 0；
- `.env` / `backups/` 被 gitignore。

未做：

- TLS/证书；
- 密钥管理/Vault；
- 依赖漏洞扫描；
- 镜像签名；
- WAF/限流；
- 渗透测试。

## 6. 运行边界

- 单机 Compose；
- 备份文件未加密；
- 无异地副本；
- 无自动恢复编排；
- 无 binlog/PITR；
- 无正式 RPO/RTO 承诺。

## 7. 后续

D3 可做：

- 备份轮转/加密/异地；
- 镜像扫描与签名；
- TLS/反向代理生产配置；
- 负载/容量基线；
- 最终验收清单。
