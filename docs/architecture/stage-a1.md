# 阶段 A1 架构与取舍

## 1. 本轮目标

A1 只回答：

1. 前端能否拿到真实门店列表？
2. 用户能否从门店进入该门店自己的菜单？
3. 后端是否按“门店归属 + 上架状态”过滤，而不是让前端自行判断？
4. 营业、接单、可点单三个状态是否被正确区分？
5. 价格从数据库到浏览器是否保持十进制语义？

不实现登录、购物车、库存、订单。不创建没有行为的空表。

## 2. 模块与调用链

```text
GET /api/stores
  StoreListView.vue
    -> stores/storeList.ts
    -> api/catalog.ts fetchStores()
    -> Vite proxy
    -> StoreController.listStores()
    -> StoreService.listStores()
    -> StoreMapper (MyBatis-Plus)
    -> stores

GET /api/stores/{id}/menu
  StoreMenuView.vue
    -> stores/storeMenu.ts
    -> api/catalog.ts fetchMenu(id)
    -> MenuController.getMenu()
    -> CatalogService.getMenu()
       -> StoreService.getStore()       校验门店存在
       -> ProductMapper                 按 store_id + on_sale 查询
    -> products
```

关键文件：

| 层 | 文件 |
| --- | --- |
| 门店 HTTP | `backend/src/main/java/com/burgerlab/store/StoreController.java` |
| 门店规则 | `backend/src/main/java/com/burgerlab/store/StoreService.java` |
| 菜单 HTTP | `backend/src/main/java/com/burgerlab/catalog/MenuController.java` |
| 菜单组装 | `backend/src/main/java/com/burgerlab/catalog/CatalogService.java` |
| 门店表 | `backend/src/main/resources/db/migration/V2__create_store_catalog.sql` |
| 种子数据 | `backend/src/main/resources/db/migration/V3__seed_stage_a1_catalog.sql` |
| 前端列表 | `frontend/src/views/StoreListView.vue` |
| 前端菜单 | `frontend/src/views/StoreMenuView.vue` |
| 前端状态 | `frontend/src/stores/storeList.ts`、`frontend/src/stores/storeMenu.ts` |

## 3. 为什么门店状态有三个字段

`stores` 有：

- `business_status`：`OPEN` / `CLOSED`，表示门店营业状态。
- `accepting_orders`：门店当前是否愿意接单，可能因繁忙、设备故障等暂停。
- DTO 中的 `orderable`：前两者的派生结果，定义为：

```text
orderable = business_status == OPEN && accepting_orders == true
```

不把“营业”和“接单”合成单个布尔值，因为“营业但暂停接单”是真实业务状态。A3 创建订单时仍必须在服务端重新校验，不能信任前端拿到的 `orderable`。

阶段 A1 只展示，不据此放行任何写操作。

## 4. 为什么菜单必须由后端过滤

菜单 SQL 条件：

```text
WHERE store_id = ?
  AND on_sale = 1
ORDER BY category_sort, sort_order, id
```

三个边界：

- **门店归属**：不同门店可以有同名商品和不同价格，不能跨店拼接菜单。
- **上架状态**：下架商品不返回给顾客，即使数据库里仍有记录。
- **稳定排序**：分类排序、商品排序、主键依次兜底，避免同一排序值下顺序漂移。

前端不做“再过滤一次”作为安全边界。前端只展示服务端给它的数据。A2/A3 的写操作仍要重新校验商品归属、上架状态和价格。

## 5. 数据访问取舍

A1 使用 MyBatis-Plus 的 `BaseMapper` + `LambdaQueryWrapper`：

- 门店列表是普通条件查询；
- 菜单是按 `store_id`、`on_sale` 过滤的普通查询；
- 排序字段明确，SQL 可读。

没有把菜单查询包装成复杂插件。后续库存条件更新、状态转换、幂等、游标分页仍使用显式 SQL，因为它们需要检查影响行数、锁顺序与执行计划。

## 6. 价格边界

- 表：`price DECIMAL(10,2)`
- Java 实体：`BigDecimal`
- API：字符串，例如 `"32.00"`
- 前端：只格式化展示，例如 `¥32.00`

Why 不直接返回 JSON number：JavaScript 的 `number` 是 IEEE-754 双精度浮点，金额在跨层计算与序列化时容易产生舍入问题。服务端保留 `BigDecimal`，JSON 用十进制字符串，是刻意的边界设计。购物车本地价格只用于展示，成交金额仍由服务端计算。

## 7. 错误语义

| 场景 | HTTP | code |
| --- | --- | --- |
| 门店不存在 | 404 | `STORE_NOT_FOUND` |
| `/api/stores/abc` | 400 | `INVALID_PARAMETER` |
| 数据库连接不可用 | 503 | `DATABASE_UNAVAILABLE` |
| 未预期异常 | 500 | `INTERNAL_ERROR` |
| 状态接口的迁移/连接故障 | 503 | 完整状态快照，见阶段 0 |

A1 发现过两个问题：

1. `spring.mvc.problemdetails.enabled=true` 时，Spring 的 ProblemDetails advice 会抢先处理路径参数转换异常，导致返回的 400 没有统一 `code`。修复方式是关闭未使用的 ProblemDetails，并让自定义 `GlobalExceptionHandler` 具备最高优先级。
2. 停库实测时，`/api/stores` 返回 500。原因是事务开启失败抛出 `CannotCreateTransactionException`，被通用异常处理器接管。先把失败测试写出来，再把 `CannotCreateTransactionException` 与 `DataAccessResourceFailureException` 映射为 503 `DATABASE_UNAVAILABLE`；恢复 MySQL 后手工确认 200。

两个问题都由实际验证发现，不是靠肉眼判断。

## 8. 前端状态设计

### 门店列表

- `loaded` 防止路由返回时重复请求。
- `refresh()` 允许用户显式重试。
- 错误保留 `UiError` 的 code、message、traceId。

### 菜单

- 切换门店时先清空旧菜单，避免把上一条门店的数据显示在新路由下。
- `requestSequence` 只允许最新请求写入状态。
- 旧请求晚到时直接丢弃；不覆盖新门店，也不错误地结束新请求的 loading。

这是一个真实的前端竞态边界，不是“多此一举”。测试用一个先返回、一个后返回的 deferred promise 证明了这一点。

## 9. 共享布局

`SiteHeader` 与 `SiteFooter` 被三个页面复用：

- 门店列表；
- 门店菜单；
- 系统状态。

共享的是导航与品牌，不是业务组件。没有为几个商品行再造一套抽象组件。

## 10. 未解决与下一步

A1 未做：

- 登录、角色、数据归属；
- 购物车与本地持久化；
- 库存与下单；
- 管理端门店/商品维护；
- 菜单图片与多语言；
- 缓存、限流、审计。

下一步 A2 应引入固定账号登录、Session 与角色入口；A2 的购物车仍只在前端，价格只作展示。
