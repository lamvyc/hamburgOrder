# 阶段 D3 发布验收手册

## 1. 性能基线

```bash
./scripts/load-baseline.sh
```

可覆盖：

```bash
LOAD_BASE_URL=http://127.0.0.1:8080 ./scripts/load-baseline.sh
```

输出：

```text
docs/evidence/stage-d3-load-menu.json
docs/evidence/stage-d3-load-status.json
```

失败条件：

- 请求错误 >0；
- P95 超过 `LOAD_P95_LIMIT_MS`（默认 1000ms）。

## 2. 发布检查

```bash
./scripts/release-check.sh
```

检查：

- 四服务 healthy；
- 18080/18081 与 Nginx 代理；
- 安全头/401/非 root/ignore；
- 最新备份存在且 600；
- 后端/前端测试计数；
- 镜像 ID/大小。

## 3. 手工验收

- 容器前端登录；
- 菜单/购物车；
- 下单/支付/取消；
- 员工工作台；
- 管理员改价/库存；
- `/system` 缓存指标；
- `/actuator/prometheus`。

## 4. 回滚

当前镜像只有 `latest`，没有 registry/不可变 tag：

- 不能证明历史版本可回滚；
- 本地只能保留旧镜像并手工 retag；
- D3 未做回滚演练。

## 5. 发布结论

工程验收通过只代表本机/当前容器状态；不是生产上线批准。
