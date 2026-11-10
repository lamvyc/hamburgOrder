# 阶段 A1 学习材料

> 先提交你的分析、代码或排查记录，再给答案。提示顺序：排查入口 → 局部线索 → 原因分析 → 完整解法。

## 1. 知识定位

层级：**L1 — HTTP、分层、SQL 过滤/排序、JSON 契约、Vue Router、Pinia 异步状态**。

本轮真实用到了：

- HTTP 200 / 400 / 404 的语义
- MyBatis-Plus 的 Mapper 与条件构造器
- 外键、复合唯一约束、CHECK 约束、组合索引
- `DECIMAL` / `BigDecimal` / JSON 十进制字符串
- Vue Router 动态路由参数
- Pinia 的 loading / error / loaded 状态
- 前端异步请求竞态与请求序号
- 同一站点下的共享布局

事务并发、库存条件更新、幂等、状态机仍不在 A1 范围内。

## 2. 一句话本质

A1 的本质不是“把门店和商品查出来”，而是：**门店归属、营业状态、接单状态和上架状态必须在正确的层被过滤和解释，前端只负责展示服务端允许它看到的数据。**

## 3. 建议阅读顺序

1. `backend/src/main/resources/db/migration/V2__create_store_catalog.sql`
2. `backend/src/main/resources/db/migration/V3__seed_stage_a1_catalog.sql`
3. `backend/src/main/java/com/burgerlab/store/StoreService.java`
4. `backend/src/main/java/com/burgerlab/catalog/CatalogService.java`
5. `backend/src/main/java/com/burgerlab/store/StoreController.java`
6. `backend/src/main/java/com/burgerlab/catalog/MenuController.java`
7. `backend/src/main/java/com/burgerlab/common/error/GlobalExceptionHandler.java`
8. `frontend/src/router/index.ts`
9. `frontend/src/stores/storeList.ts`
10. `frontend/src/stores/storeMenu.ts`
11. `frontend/src/views/StoreListView.vue`
12. `frontend/src/views/StoreMenuView.vue`

## 4. 阅读问题

1. `StoreService.toResponse()` 里的 `orderable` 是如何计算出来的？为什么后端已经返回它，A3 创建订单时仍不能信任前端传来的 `orderable`？
2. `CatalogService.getMenu()` 的 SQL 条件同时包含 `store_id` 与 `on_sale`。分别去掉其中一个，会产生什么业务错误或数据泄露？请各举一个具体例子。
3. 菜单价格在数据库、Java、JSON、Vue 中分别是什么类型？为什么不直接返回 JSON number？如果 A2 购物车做“数量 × 单价”，哪里才是成交金额的权威计算点？
4. `useStoreMenuStore.load()` 中的 `requestSequence` 解决什么时序问题？请用“请求 A、请求 B、响应 B、响应 A”的时间线说明；如果删掉它，页面可能出现什么错误状态？
5. `/api/stores/999` 与 `/api/stores/999/menu` 都返回 404，而“门店存在但没有在售商品”返回 200 + `categories=[]`。为什么这个区分对前端有用？`/api/stores/abc` 为什么是 400 而不是 404？

## 5. 独立开发任务

**任务：给菜单接口增加服务端分类过滤，并让菜单页支持分类筛选。**

目标契约（可以调整字段名，但要写清理由）：

```text
GET /api/stores/{storeId}/menu?category=BURGER
```

约束：

- 不带 `category` 时行为与现在完全一致。
- 带 `category` 时只返回该分类，分类内商品排序不变。
- `category` 为空串、超长或不存在时，必须明确选择一种语义并写测试证明：
  - 方案 A：400 + 业务错误码；
  - 方案 B：200 + 空分类。
  选择哪一种，并说明对前端和运营的影响。
- 不允许前端拿到全量菜单后再过滤来冒充服务端过滤。
- 后端补集成测试；前端补 store 测试；菜单页分类按钮写入 URL query，刷新后仍保持筛选。
- 不修改阶段 0 健康接口，不引入登录或购物车。

完成后提交：接口契约、测试结果、一个成功请求、一个非法请求、一段你对“为什么过滤必须发生在服务端”的解释。

## 6. 故障定位练习

**场景**

中央车站店在数据库里有 7 条 `products` 记录，但菜单页面只显示 6 个商品。运营说“少了一个海盐奶昔”。

请在不修改代码的前提下给出排查方案：

1. 你先查哪三个证据？分别证明什么？
2. 如何判断是“种子数据没插入”“`on_sale=0` 被过滤”“SQL 条件写错”“API 返回后前端又过滤掉了”中的哪一种？
3. 给出至少两条 SQL 或 HTTP 命令，并说明预期输出如何支持或推翻假设。
4. 如果运营要求“下架商品也要在顾客菜单可见”，应该改视图、改接口还是改数据？说明边界。

先提交判断，不要先问答案。

## 7. 脱稿口述题

用 90 秒解释：

> “门店列表里的 `businessStatus`、`acceptingOrders`、`orderable` 分别是什么？为什么菜单接口必须同时限定门店和上架状态？价格为什么用字符串跨 JSON 边界？”

要求：

- 至少提到一个真实类名、一个 SQL 条件和一个 HTTP 状态码；
- 不照读代码；
- 明确区分“页面能显示”和“服务端允许下单”。

## 8. 四级学习状态

| 能力 | 判定证据 | 当前状态 |
| --- | --- | --- |
| 功能已实现 | 代码与运行验证 | 工程侧已完成 |
| 能解释机制 | 回答阅读问题并解释状态与过滤 | 未评估 |
| 能独立实现相近功能 | 完成分类过滤任务 | 未提交 |
| 能独立定位故障 | 完成 7 条记录只显示 6 条练习 | 未提交 |

阶段 0 的练习也仍未提交，两项一起记录，不重复催确认。

## 9. 前置知识与补课边界

- SQL：`WHERE`、`ORDER BY`、外键、复合唯一约束、组合索引
- HTTP：404 与 400 的区别
- Java：`BigDecimal` 与 `DECIMAL` 的边界
- MyBatis-Plus：Mapper、`LambdaQueryWrapper`
- Vue Router：动态参数
- Pinia：异步请求的 loading/error/竞态
- 前端金额格式化只用于展示

不要求本轮掌握：库存行锁、事务隔离、幂等表、消息可靠性。

## 10. 提交方式

直接提交：

- 阅读问题答案；
- 分类过滤任务代码/补丁与测试；
- 故障练习的假设、命令和输出；
- 口述题提纲或转写。

我会先审查你的推理，再按提示层级补充。
