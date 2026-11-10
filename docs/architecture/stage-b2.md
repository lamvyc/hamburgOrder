# 阶段 B2 架构：管理端商品与库存

## 1. 目标

B2 增加真正的管理员写操作：

1. 管理员查看门店全部商品（含下架）。
2. 改价格、上下架，使用商品版本乐观锁。
3. 调整可售库存，使用库存版本乐观锁。
4. 库存不能调成负数。
5. 库存调整写 ADJUST 流水（`order_id` 为空、记录操作者）。
6. 管理操作写审计日志。
7. 顾客/员工不能调用管理接口。
8. 前端管理台展示商品、库存、版本和最近审计。

## 2. 权限

所有 `/api/admin/**` 只允许 ROLE_ADMIN。

- CUSTOMER → 403；
- STAFF → 403；
- ADMIN → 允许；
- 未登录 → 401。

这不是前端隐藏菜单；控制器与 Spring Security 都是真实边界。

## 3. 商品乐观锁

`products.version` V11 新增。

```sql
UPDATE products
SET price=?, on_sale=?, version=version+1
WHERE id=? AND version=?;
```

- affected=1：成功；
- affected=0：409 `VERSION_CONFLICT`；
- 旧版本不能覆盖新价格/上下架状态；
- 已有订单使用 `order_items` 成交快照，不受改价影响；
- 下架商品从公开菜单消失，但管理员列表仍可见。

## 4. 库存乐观锁

`stock.version` 已存在。

```sql
UPDATE stock
SET available = available + delta,
    version = version + 1
WHERE store_id=?
  AND product_id=?
  AND version=?
  AND available + delta >= 0;
```

- delta 非 0；
- `reserved` 不变；
- affected=0 时重新读取：
  - 版本不同 → 409 `VERSION_CONFLICT`；
  - `available + delta < 0` → 409 `STOCK_INSUFFICIENT`；
  - 其他 → 409。
- 并发两个管理员使用同一版本，只有一个成功。

## 5. 库存流水

V11 允许 `stock_logs.order_id` 为空，并新增 `operator_user_id`。

管理员调整写入：

```text
change_type=ADJUST
order_id=NULL
operator_user_id=admin
available_delta=delta
reserved_delta=0
available_after / reserved_after
reason=管理员填写
```

订单预占/提交/释放仍是 RESERVE/COMMIT/RELEASE，`order_id` 非空。

## 6. 审计

`admin_audit_logs`：

- actor_user_id；
- action（PRODUCT_UPDATE / STOCK_ADJUST）；
- target_type/target_id；
- detail JSON（前后值、delta、原因）；
- created_at。

审计与业务更新同一事务。写审计失败会回滚业务更新。

## 7. 查询

管理员商品列表：

```sql
SELECT p..., s...
FROM products p
JOIN stock s ON s.product_id=p.id AND s.store_id=p.store_id
WHERE p.store_id=?
ORDER BY p.category_sort, p.sort_order, p.id;
```

实际执行计划：

```text
products: ref uk_products_store_code, rows=7, Using filesort
stock: eq_ref PRIMARY, rows=1
```

当前每家店 7 个商品，filesort 成本可接受；B3 再评估管理列表专用索引。

## 8. 前端

`/admin` 保持 A2 入口卡片，新增 `/admin/catalog`：

- 门店切换；
- 商品表：价格、上下架、可售、预占、商品/库存版本；
- 改价/上下架对话框；
- 调整库存对话框；
- 最近审计；
- 409 显示错误码与 traceId。

Element Plus 仍按需注册；新增 `ElDialog`、`ElSwitch` 必须同步注册组件与样式，否则浏览器把它们当未知标签。这是 B2 E2E 实际发现的集成问题。

## 9. 未实现

- 新建/删除商品；
- 门店营业/接单状态修改；
- 批量导入/导出；
- 审批流；
- 图片、分类管理；
- 管理列表游标分页；
- 操作原因必填的更强审计策略；
- 角色细分（店长/运营）。
