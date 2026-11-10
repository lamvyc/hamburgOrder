# 阶段 B5 排错入口

学习故障练习在 [阶段 B5 学习材料](../learning/stage-b5.md)。

## 1. 预览没有结果

- 前端是否在预解析阶段直接拒绝（B5 实际踩到：delta=0 被前端拦掉，预览请求根本没发）；
- 预览应允许零/非法行交给后端逐行报告；
- 检查 Network 的 `/batch/preview` 状态；
- 检查权限/CSRF。

## 2. 预览通过但执行失败

这是合法 TOCTOU：

- 订单占用了库存；
- 另一个管理员调整了库存；
- 门店/商品状态变化；
- 执行条件更新 affected=0。

处理：显示执行错误，刷新库存；不要重试到成功，除非业务允许。

## 3. 预览写入了数据

检查：

- 是否调用了 B4 执行接口而不是 preview；
- 预览服务是否误用写事务；
- 是否在预览里插入 batch/log/audit；
- 数据库计数验证。

## 4. 错误码不对

- `INVALID_STOCK_DELTA`：delta=0；
- `DUPLICATE_BATCH_PRODUCT`：同一批重复 productId；
- `PRODUCT_NOT_FOUND`：商品不存在或门店不匹配；
- `STOCK_INSUFFICIENT`：available+delta<0。

行号从 1 开始，与输入顺序一致（预览不排序）。

## 5. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:b5
```
