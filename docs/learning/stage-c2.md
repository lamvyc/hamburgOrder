# 阶段 C2 学习材料

> 先提交代码/分析，再给答案。

## 1. 知识定位

层级：**L2/L3 — single-flight、负缓存、TTL 抖动、缓存指标、降级边界**。

本轮用到了：

- `ConcurrentHashMap` + `CompletableFuture` 本地 single-flight
- 500ms 等待上限
- `__MISSING__` 负缓存
- TTL 60 + 0–15 抖动
- HIT/MISS/BYPASS/negative/stampedeWait 计数
- 公开只读统计端点
- Redis Testcontainer 并发测试

## 2. 一句话本质

C2 的本质不是“再加几个缓存技巧”，而是：**缓存系统要能承受并发回源、无效 key 和集中过期，同时让这些行为可观测，并明确只解决了哪一层。**

## 3. 阅读问题

1. 本地 single-flight 如何保证同实例只回源一次？为什么 followers 仍要设置等待超时，而不是无限等？
2. 负缓存为什么 TTL 要比正常缓存短？如果门店后来被创建，10 秒内会发生什么？
3. TTL 抖动如何降低雪崩？为什么不能用完全随机的大范围 TTL 代替容量规划？
4. `stampedeWait` 计数说明什么？它变大不一定是故障，什么情况下需要告警？
5. 单实例 single-flight 与 Redis 分布式 single-flight 的差别？多实例下当前实现会发生什么？

## 4. 独立开发任务

**任务：Redis 分布式 single-flight。**

要求：

- `SET lock NX EX` 抢锁；
- 释放锁用 token 比较（Lua），避免删别人的锁；
- 未抢到锁的请求短暂等待缓存，超时后回源；
- 锁 TTL 覆盖最慢 DB 查询；
- 回源失败释放锁；
- 补多实例/并发/锁超时测试；
- 不实现多实例失效广播。

## 5. 故障定位练习

**场景**

同一门店菜单 QPS 很高，Redis 里 key 刚好过期。监控看到 MySQL 同时出现 20 个相同菜单查询，`stampedeWait=0`。

请排查：

1. 先查哪三个证据？
2. 如何区分“single-flight 没生效”“多个实例各自 single-flight”“key 被频繁删除”“等待超时直接回源”？
3. 给出两条日志/指标证据。
4. 修复应放在本地 map、Redis 锁、TTL 还是缓存预热？

## 6. 脱稿口述题

用 90 秒解释：

> “single-flight 如何避免缓存击穿？负缓存如何防穿透？TTL 抖动如何缓解雪崩？这些指标说明什么？”

要求：

- 提到等待上限、`__MISSING__`、TTL 抖动、单实例边界；
- 说明一个多实例反例；
- 不照读代码。

## 7. 四级学习状态

| 能力 | 证据 | 当前 |
| --- | --- | --- |
| 功能已实现 | 测试、E2E、计数 | 工程侧完成 |
| 能解释机制 | 阅读问题 | 未评估 |
| 能独立实现相近功能 | Redis 分布式 single-flight | 未提交 |
| 能独立定位故障 | 回源风暴 | 未提交 |

## 8. 前置知识

- 缓存更新模式
- 穿透/击穿/雪崩
- CompletableFuture/并发
- Redis NX/EX/Lua
- TTL 设计
- 指标观测

## 9. 提交方式

提交阅读问题答案、分布式 single-flight 代码/测试、回源风暴排查、口述提纲。
