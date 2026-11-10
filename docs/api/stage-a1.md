# 阶段 A1 API 契约

OpenAPI 文件：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 范围

A1 新增三个只读端点：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/stores` | 门店列表 |
| GET | `/api/stores/{storeId}` | 门店详情 |
| GET | `/api/stores/{storeId}/menu` | 当前门店的在售菜单 |

没有认证。A1 的读接口公开，只适合本地开发；A2 引入 Session 后需要重新定义哪些端点公开。

## 2. GET /api/stores

成功：HTTP 200

```json
{
  "items": [
    {
      "id": 1,
      "code": "CENTRAL",
      "name": "中央车站店",
      "address": "静安区南京西路 88 号",
      "latitude": 31.2304,
      "longitude": 121.4737,
      "businessStatus": "OPEN",
      "acceptingOrders": true,
      "orderable": true,
      "openingHours": "10:00–22:00"
    },
    {
      "id": 2,
      "code": "RIVERSIDE",
      "name": "滨江店",
      "address": "浦东新区滨江大道 66 号",
      "latitude": 31.2397,
      "longitude": 121.4998,
      "businessStatus": "OPEN",
      "acceptingOrders": false,
      "orderable": false,
      "openingHours": "11:00–23:00"
    },
    {
      "id": 3,
      "code": "OLD_TOWN",
      "name": "老城店",
      "address": "黄浦区方浜中路 21 号",
      "latitude": 31.2271,
      "longitude": 121.492,
      "businessStatus": "CLOSED",
      "acceptingOrders": false,
      "orderable": false,
      "openingHours": "09:00–21:00"
    }
  ]
}
```

字段语义：

- `businessStatus=OPEN`：门店处于营业状态。
- `acceptingOrders`：门店当前是否愿意接单，独立于营业状态。
- `orderable`：`OPEN && acceptingOrders` 的服务端派生值；A1 仅用于展示。
- 排序：按 `id` 升序，当前数据量小，没有分页。

## 3. GET /api/stores/{storeId}

成功：HTTP 200，返回单个门店对象，字段与列表项相同。

不存在的门店：HTTP 404

```json
{
  "code": "STORE_NOT_FOUND",
  "message": "门店不存在",
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-11-09T10:00:00Z",
  "path": "/api/stores/999"
}
```

非法 ID：HTTP 400

```json
{
  "code": "INVALID_PARAMETER",
  "message": "请求参数格式不正确",
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-11-09T10:00:00Z",
  "path": "/api/stores/abc"
}
```

## 4. GET /api/stores/{storeId}/menu

成功：HTTP 200

```json
{
  "store": {
    "id": 1,
    "code": "CENTRAL",
    "name": "中央车站店",
    "address": "静安区南京西路 88 号",
    "latitude": 31.2304,
    "longitude": 121.4737,
    "businessStatus": "OPEN",
    "acceptingOrders": true,
    "orderable": true,
    "openingHours": "10:00–22:00"
  },
  "categories": [
    {
      "code": "BURGER",
      "name": "汉堡",
      "products": [
        {
          "id": 1001,
          "code": "S1-CLASSIC",
          "name": "经典牛肉堡",
          "description": "手打牛肉饼、切达芝士、酸黄瓜与招牌酱",
          "price": "32.00"
        }
      ]
    }
  ]
}
```

语义：

1. 门店必须存在；否则 404，而不是空菜单。
2. 只返回 `products.store_id = {storeId}` 的商品。
3. 只返回 `on_sale = 1` 的商品；A1 种子里中央车站店有一个下架奶昔，不会出现。
4. 分类按 `category_sort`，商品按 `sort_order`、`id` 稳定排序。
5. 门店休息或暂停接单时菜单仍可浏览；`orderable=false` 由前端展示提示。A3 下单时必须再次校验。
6. 无法解析的路径 ID 返回 400。

空菜单：HTTP 200，`categories=[]`。这不是 404；404 只表示“门店不存在”。

### 价格为什么是字符串

```json
"price": "32.00"
```

- 数据库：`DECIMAL(10,2)`
- Java：`BigDecimal`
- JSON：十进制字符串
- 前端：仅格式化展示

直接返回 JSON number 会让金额落入 JavaScript 的 IEEE-754 浮点数。A1 虽然没有计算成交金额，但从边界开始保持十进制语义，避免 A2/A3 在购物车和订单金额上补漏洞。

## 5. 通用错误

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `INVALID_PARAMETER` | 路径参数不是合法整数 |
| 404 | `STORE_NOT_FOUND` | 门店不存在 |
| 404 | `NOT_FOUND` | 路由不存在 |
| 500 | `INTERNAL_ERROR` | 未预期异常 |
| 503 | `DATABASE_UNAVAILABLE` | 门店/菜单查询无法获得数据库连接 |

- 所有响应带 `X-Trace-Id`。
- 500 不返回堆栈、SQL、数据库地址或凭据。
- 404/400 均使用统一 `ApiErrorResponse`。

## 6. 测试覆盖

- 3 家门店及三种状态组合；
- 门店详情与未知门店 404；
- 菜单分类、排序、在售过滤；
- 休息中门店仍可浏览菜单；
- 非法路径参数 400；
- 不存在的菜单门店 404；
- 前端门店/菜单 store 与过期响应竞争。

实际测试记录见 [A1 验证证据](../evidence/stage-a1.md)。
