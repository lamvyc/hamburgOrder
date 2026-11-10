# 阶段 D3 排错入口

## 1. 负载脚本失败

- `LOAD_REQUESTS` 1–5000；
- `LOAD_CONCURRENCY` 1–200；
- `LOAD_BASE_URL` 是否包含协议；
- 容器 18080 是否 healthy；
- P95 阈值是否过低。

## 2. 菜单 P95 高

- 是否预热；
- `X-Cache` 是否大量 MISS；
- Redis 是否故障/BYPASS；
- single-flight 是否生效；
- 是否多实例各自回源。

## 3. 状态接口 P95 高

- DB 连接池；
- Flyway/状态查询是否重复；
- 并发是否超过 Hikari 池；
- 慢查询/锁。

## 4. 发布检查失败

- compose 服务 unhealthy；
- 18080/18081 端口；
- 安全头/401/非 root；
- 最新备份不存在或权限不是 600；
- 测试报告缺失（先跑测试）。

## 5. 回滚

当前没有不可变 tag/registry；不能把 `latest` 当成可回滚版本。需要先实现 D1 练习。

## 6. 命令

```bash
./scripts/load-baseline.sh
./scripts/release-check.sh
```
