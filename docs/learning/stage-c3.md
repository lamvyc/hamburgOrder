# 阶段 C3 学习材料

> 先提交代码/分析，再给答案。

## 1. 知识定位

层级：**L2/L3 — Micrometer 指标、Prometheus、标签基数、HTTP 直方图、观测端点、系统页**。

本轮用到了：

- Counter（缓存计数）
- HTTP server metrics
- Prometheus registry/endpoint
- Actuator access/exposure
- Accept 内容协商
- System 页缓存面板
- 进程本地计数

## 2. 一句话本质

C3 的本质不是“加一个 /actuator/prometheus”，而是：**指标要有受控标签、可解释语义、正确的内容类型和明确的观测边界。**

## 3. 阅读问题

1. 为什么缓存计数器的 `result` 用枚举标签，而不用 storeId？高基数标签会带来什么？
2. `http_server_requests_seconds_count` 与直方图 bucket 分别回答什么问题？P95 如何从 bucket 近似？
3. Prometheus 为什么是拉模型？如果应用只暴露文本端点但没有抓取配置，算不算“监控完成”？
4. `MenuCacheStats` 是进程本地计数；多实例下如何聚合？重启归零对告警有什么影响？
5. Prometheus 端点用 `Accept: application/json` 返回 500，而不是 JSON 错误——原因是什么？应该返回什么更合适？

## 4. 独立开发任务

**任务：给菜单缓存建立 SLO 与告警规则草案。**

要求：

- 定义 hit ratio 指标与目标；
- 定义 HTTP P95/P99 目标；
- 写 PromQL（hit ratio、5xx 比例、P95）；
- 说明多实例聚合与重启影响；
- 定义告警阈值/持续时间；
- 不要求实际接 Alertmanager；
- 补一段可验证的本地 PromQL 查询记录。

## 5. 故障定位练习

**场景**

系统页缓存 HIT/MISS 都有数据，但 `/actuator/prometheus` 返回 404；另一个环境返回 500。

请排查：

1. 先查哪三个证据？
2. 如何区分“依赖缺失”“endpoint access NONE”“exposure 未包含”“enabled/access 互斥”“Accept 不匹配”？
3. 给出两条命令/配置证据。
4. 修复应放在依赖、配置还是控制器？

## 6. 脱稿口述题

用 90 秒解释：

> “指标如何从代码进入 Prometheus？缓存计数器为什么用受控标签？HTTP 直方图怎么支持 P95？进程本地指标有什么局限？”

要求：

- 提到 Counter/直方图、标签基数、Prometheus pull；
- 说明一个端点 404/500 的排查点；
- 不照读代码。

## 7. 四级学习状态

| 能力 | 证据 | 当前 |
| --- | --- | --- |
| 功能已实现 | 测试、E2E、Prometheus | 工程侧完成 |
| 能解释机制 | 阅读问题 | 未评估 |
| 能独立实现相近功能 | SLO/告警草案 | 未提交 |
| 能独立定位故障 | 404/500 端点 | 未提交 |

## 8. 前置知识

- Micrometer 指标类型
- Prometheus 拉模型
- 标签基数
- HTTP 直方图/分位数
- Actuator 安全/暴露
- 进程本地 vs 多实例

## 9. 提交方式

提交阅读问题答案、SLO/告警草案、端点排查、口述提纲。
