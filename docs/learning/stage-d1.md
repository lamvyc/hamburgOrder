# 阶段 D1 学习材料

> 先提交答案/配置/命令，再给解答。

## 1. 知识定位

层级：**L2/L3 — 多阶段镜像、Nginx 反代、Compose profile、健康依赖、CI 流水线、部署验证**。

本轮用到了：

- Maven build stage + JRE runtime stage
- pnpm build stage + Nginx runtime
- 非 root 容器
- Nginx `/api` `/actuator` proxy 与 SPA fallback
- Compose profiles/healthcheck/depends_on
- 本地部署/验证脚本
- GitHub Actions YAML
- 容器化浏览器登录验证

## 2. 一句话本质

D1 的本质不是“写个 Dockerfile”，而是：**把构建、运行、代理、依赖顺序、健康检查和验证串成一条可重复的交付链。**

## 3. 阅读问题

1. 后端为什么用多阶段镜像？如果直接把 Maven/JDK 放进运行镜像，体积/攻击面/启动有什么代价？
2. 前端为什么用 Nginx 而不是 `vite preview`？Nginx 如何同时处理 SPA 路由与 `/api` 反向代理？
3. 为什么 backend 要依赖 mysql/redis healthy，frontend 又依赖 backend healthy？只写 `depends_on` 不写 condition 会怎样？
4. Compose `profiles: ["app"]` 解决了什么？为什么日常开发仍保留 host 的 8080/5173？
5. CI 中“测试通过”和“镜像构建成功”是同一件事吗？为什么 images job 要单独 build？

## 4. 独立开发任务

**任务：用 Git SHA 打镜像标签并增加发布流水线草案。**

要求：

- 镜像 tag 使用 `git rev-parse --short HEAD`；
- backend/frontend 同 tag；
- CI 只 build，不 push（除非明确配置 registry）；
- 写 GHCR push 草案（权限/token）；
- 说明 latest 与不可变 tag 的区别；
- 不实际推送公共仓库。

## 5. 故障定位练习

**场景**

`docker compose --profile app up` 后，frontend 能打开 HTML，但所有 `/api` 请求 502；backend 容器日志正常，host 的 8080 却可访问。

请排查：

1. 先查哪三个证据？
2. 如何区分 Nginx upstream 名称错误、端口错误、Compose 网络、backend 未 healthy、proxy_pass 路径重复？
3. 给出两条命令证据。
4. 修复应放在 nginx.conf、compose service name 还是端口映射？

## 6. 脱稿口述题

用 90 秒解释：

> “代码如何变成两个镜像？前端如何代理 API？容器依赖如何保证顺序？本地部署如何验证成功？”

要求：

- 提到多阶段、Nginx、healthcheck、profile、验证命令；
- 说明一个 502 排查点；
- 不照读 Dockerfile。

## 7. 四级学习状态

| 能力 | 证据 | 当前 |
| --- | --- | --- |
| 功能已实现 | 镜像构建、容器 E2E | 工程侧完成 |
| 能解释机制 | 阅读问题 | 未评估 |
| 能独立实现相近功能 | Git SHA/GHCR 草案 | 未提交 |
| 能独立定位故障 | API 502 | 未提交 |

## 8. 前置知识

- Docker 多阶段构建
- 镜像层/缓存
- Nginx proxy_pass
- Compose profiles/healthcheck
- CI YAML
- 容器网络/DNS
- Session/CSRF 同源

## 9. 提交方式

提交阅读问题答案、Git SHA/GHCR 草案、502 排查、口述提纲。
