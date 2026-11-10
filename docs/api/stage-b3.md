# 阶段 B3 API 契约

OpenAPI：[`docs/api/openapi.yaml`](openapi.yaml)

## 1. 管理员商品分页

```http
GET /api/admin/stores/{storeId}/products/page?limit=5&afterId=1005&onSale=true
```

权限：ADMIN。

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `afterId` | 否 | 上一页最后商品 id；第一页省略 |
| `limit` | 否 | 1–50，默认 10 |
| `onSale` | 否 | true 只看上架；false 只看下架；省略全部 |
| `storeId` | 是 | 路径门店 |

成功 200：

```json
{
  "items": [
    {
      "id": 1006,
      "storeId": 1,
      "code": "S1-COLA",
      "name": "冰镇可乐",
      "categoryName": "饮品",
      "price": "10.00",
      "onSale": true,
      "version": 0,
      "available": 99,
      "reserved": 1,
      "stockVersion": 11
    }
  ],
  "nextCursor": 1006,
  "hasMore": true
}
```

- 按 `id ASC` 稳定排序；
- `nextCursor` 是本页最后 id；
- `hasMore=false` 时 `nextCursor=null`；
- 不使用 OFFSET；
- 多取一条判断 hasMore。

错误：

- 401 未登录；
- 403 非 ADMIN；
- 404 `STORE_NOT_FOUND`；
- 参数越界由服务端限制到 1–50（`limit` 非整数则 400）。

## 2. 与旧列表的关系

```text
GET /api/admin/stores/{storeId}/products
```

仍返回全部商品，兼容 B2 测试/调用；管理台已改用分页接口。B3 不删除旧接口。

## 3. 索引行为

Mapper 对分页查询显式：

```text
有 onSale -> FORCE INDEX (idx_products_store_onsale_id)
无 onSale -> FORCE INDEX (idx_products_store_id)
```

原因：小数据下优化器会选唯一键 + filesort。FORCE 是保证 keyset 排序使用索引的显式取舍。

## 4. 未实现

- 关键词搜索；
- 随机跳页/总数；
- 多字段游标；
- 审计日志分页；
- 百万级压测。
