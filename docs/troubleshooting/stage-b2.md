# 阶段 B2 排错入口

学习故障练习在 [阶段 B2 学习材料](../learning/stage-b2.md)。

## 1. 管理接口 403

- 当前用户是否 ADMIN；
- 是否带 Session；
- PATCH/POST 是否带 CSRF；
- 前端路由守卫不是权限边界。

## 2. 409 VERSION_CONFLICT

商品：

```sql
SELECT id, price, on_sale, version FROM products WHERE id=?;
```

库存：

```sql
SELECT product_id, available, reserved, version FROM stock WHERE product_id=?;
```

- 客户端提交的版本是否旧；
- 是否有另一个管理员先保存；
- 刷新管理列表后重新操作；
- 不要用“后写覆盖”来绕过版本。

## 3. 409 STOCK_INSUFFICIENT

库存调整条件：

```sql
available + delta >= 0
```

若 `available=3`，delta=-5，即使版本正确也应拒绝。检查：

```sql
SELECT available, reserved, version FROM stock WHERE product_id=?;
```

不能用调整 available 的方式去“修复”负库存或错误预占；先查订单/流水。

## 4. 库存调整成功但审计没有

- 审计插入是否与库存更新同一事务？
- 审计 detail JSON 是否合法？
- 操作者 id 是否存在？
- 是否只写了 stock_logs 而漏了 admin_audit_logs？

```sql
SELECT order_id, operator_user_id, change_type, available_delta, reason
FROM stock_logs WHERE product_id=? ORDER BY id DESC LIMIT 5;

SELECT actor_user_id, action, target_id, detail
FROM admin_audit_logs WHERE target_id=? ORDER BY id DESC LIMIT 5;
```

## 5. 管理页面按钮无反应

B2 实际遇到过：Element Plus 按需注册，新使用 `el-dialog`/`el-switch` 但未注册，浏览器把它们当未知标签，点击后没有对话框。

检查：

- `main.ts` 是否 `app.use(ElDialog)` / `app.use(ElSwitch)`；
- 是否引入对应 `style/css`；
- 浏览器 console 是否有 unknown custom element 警告；
- DOM 里是否出现原始 `<el-dialog>` 标签。

## 6. 表格文字背景异常拉长

`code` 等 grid 子项默认 stretch，背景会拉满单元格。对首列使用 `justify-items:start`，审计 code 使用 `justify-self:start; background:transparent`。

## 7. 测试

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock" \
mvn -f backend/pom.xml test

cd frontend
pnpm test
pnpm test:e2e:b2
```
