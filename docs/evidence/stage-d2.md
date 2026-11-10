# 阶段 D2 实际验证记录

验证时间：2026-11-09 23:15–23:23（Asia/Shanghai）
范围：MySQL 一致性备份、临时库恢复、Redis 故障降级、容器安全检查。
只对应本机实际结果，不代表生产 RPO/RTO。

## 1. 备份

```text
backup=/Users/unravel/hamburgOrder/backups/burger_order-20261109T152056Z.sql.gz
bytes=10798
sha256=7fd1a24718119ca570a3937fdafe0498469e228a7247163589f3ee2e028269cf
mode=600
```

使用：

```text
mysqldump --single-transaction --routines --triggers
         --no-tablespaces --set-gtid-purged=OFF
gzip -9
```

## 2. 恢复演练

```text
restore drill passed
scratch=burger_order_restore_check
flywayVersion=13
admin_audit_logs 20
admin_stock_batch_lines 8
admin_stock_batches 4
coupon_templates 2
idempotency_records 22
order_items 29
orders 22
products 18
stock 18
stock_logs 82
stores 3
user_coupons 1
users 4
```

源库与临时库逐表 COUNT 一致；临时库已删除。

## 3. Redis 故障降级

```text
redis fallback passed: status=200 X-Cache=BYPASS
redis restarted: Up 5 seconds (healthy)
```

实际停止/启动 Redis，验证的是故障路径，不是只测正常缓存。

## 4. 安全检查

```text
security check passed
frontend headers: nosniff/DENY/referrer
unauthenticated /api/orders=401
backend container uid=999
.env and backups/ are ignored
```

## 5. 测试计数

- 后端：66/66
- 前端：30/30
- Playwright：13 条（A2–D1）
- D2 没有新增业务测试；新增的是真实运维脚本验证。

## 6. 实际发现与修复

- `security-check.sh` 最初使用 bash 只读变量 `UID`，改成 `CONTAINER_UID`。
- 备份文件最初为默认权限，脚本增加 `chmod 600`。
- 没有编造异地副本、PITR、加密或生产恢复能力。

## 7. 已验证与未验证

### 已验证

- 一致性备份生成；
- gzip + SHA256；
- 临时库恢复；
- 逐表对账；
- Flyway V13；
- Redis 故障 BYPASS；
- 安全头/401/非 root/忽略文件。

### 未验证

- binlog/PITR；
- 备份加密/异地/轮转；
- 自动恢复；
- RPO/RTO 承诺；
- MySQL 主机级故障；
- 生产 TLS/WAF/漏洞扫描；
- 灾难恢复演练。

## 8. 复现

```bash
./scripts/backup-db.sh
./scripts/restore-drill.sh
./scripts/redis-fallback-check.sh
./scripts/security-check.sh
```
