# 阶段 D3 架构：性能基线与最终验收

## 1. 目标

D3 做交付前最后一轮工程验收：

1. 对容器化后端采集真实 GET 性能基线。
2. 覆盖缓存菜单与 DB 状态两个不同路径。
3. 记录 P50/P95/P99、吞吐、错误数、X-Cache 分布。
4. 生成发布检查脚本，聚合容器/部署/安全/备份/测试/镜像。
5. 明确本地基线不是生产 SLA。

## 2. 负载脚本

`scripts/load-smoke.mjs`：

- Node 内置 `fetch`，无额外压测依赖；
- 指定 URL/请求数/并发/预热；
- worker pool 控制并发；
- 记录 headers `X-Cache` 分布；
- 输出 JSON；
- 失败条件：HTTP 错误 >0 或 P95 > 阈值（默认 1000ms）。

`scripts/load-baseline.sh`：

```text
menu  200 req / 20 concurrency
status 100 req / 10 concurrency
-> docs/evidence/stage-d3-load-*.json
```

只读 GET，不写数据库。

## 3. 发布检查

`scripts/release-check.sh`：

```text
compose ps
verify-deploy（backend/frontend/proxy）
security-check（headers/401/non-root/ignore）
latest backup + meta
backend test count
frontend test count
docker image IDs/sizes
```

它是“证据聚合器”，不是自动部署器。

## 4. 基线边界

- 单机 Colima/Docker；
- 容器后端 + Redis/MySQL 同机；
- 菜单缓存已预热，绝大多数为 HIT；
- 未做数据库写路径/订单压测；
- 未做网络延迟/故障注入下的容量测试；
- 数值不可外推为生产容量。

## 5. 未实现

- k6/Gatling 场景与 SLA；
- 长时间 soak；
- DB 慢查询分析；
- 自动回滚演练；
- 生产监控/告警联动；
- 容量规划。
