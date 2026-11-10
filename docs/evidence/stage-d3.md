# 阶段 D3 实际验证记录

验证时间：2026-11-09 23:10–23:28（Asia/Shanghai）
范围：容器后端性能基线、发布检查、最终验收。
只对应本机 Colima/Docker 环境，不是生产 SLA/容量。

## 1. 测试与迁移

- 无新增 Flyway 迁移，仍为 V13。
- 后端 66/66。
- 前端 30/30。
- Playwright 13 条（D3 无新增业务 E2E）。

## 2. 菜单负载

```text
baseUrl=http://127.0.0.1:18080
path=/api/stores/1/menu
requests=200
concurrency=20
warmup=5
errors=0
elapsedMs=135.81
throughputRps=1472.62
min=2.59ms
p50=11.73ms
p95=30.94ms
p99=33.71ms
max=36.09ms
cache: HIT=204, MISS=1
```

原始 JSON：[stage-d3-load-menu.json](stage-d3-load-menu.json)。

## 3. 状态接口负载

```text
baseUrl=http://127.0.0.1:18080
path=/api/system/status
requests=100
concurrency=10
warmup=5
errors=0
elapsedMs=280.02
throughputRps=357.12
min=2.70ms
p50=5.55ms
p95=225.44ms
p99=230.22ms
max=230.32ms
```

原始 JSON：[stage-d3-load-status.json](stage-d3-load-status.json)。

状态接口包含 DB 连接/迁移读取，P95 明显高于缓存菜单。

## 4. 发布检查

输出：[stage-d3-release.txt](stage-d3-release.txt)

关键：

```text
backend/frontend/mysql/redis healthy
deployment verification passed
security check passed
backend tests 66
frontend tests 30
frontend image fd62c984fdee 76.6MB
backend image c14b69d5dd85 581MB
latest backup 10798 bytes mode600
```

## 5. 实际发现

- 负载脚本只读 GET，不污染数据库。
- 菜单 204 HIT / 1 MISS 说明预热与缓存生效。
- 状态接口 P95 约 225ms，明显高于菜单；未做进一步 DB 慢查询分析。
- 本地单机容器、同机 MySQL/Redis，数值不能外推生产。
- 没有不可变镜像 tag/registry，因此未做真实回滚演练。
- 没有编造 SLA/容量结论。

## 6. 已验证与未验证

### 已验证

- 可复现负载脚本；
- P50/P95/P99/吞吐/错误；
- 缓存 HIT/MISS 分布；
- 发布检查聚合；
- 容器/安全/备份/测试/镜像门禁。

### 未验证

- 5 分钟以上 soak；
- 写路径/订单压测；
- DB 慢查询与连接池调优；
- 自动回滚演练；
- 生产网络/多实例/容量；
- 生产 SLA。
