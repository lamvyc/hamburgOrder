# 阶段 B5 架构：批量导入预览（dry-run）

## 1. 目标

B5 在 B4 全有或全无批次之前增加只读预览：

1. 管理员输入批次行。
2. 预览逐行校验：
   - 商品是否属于门店；
   - delta 是否非 0；
   - 同一批是否重复商品；
   - `available + delta` 是否非负。
3. 返回每行 valid/code/message 与预计前后库存。
4. 预览绝不写 stock/batch/log/audit。
5. 正式提交仍走 B4 的幂等全有或全无批次。
6. 前端在提交前显示逐行结果。

## 2. 为什么预览与执行分离

预览是读操作：

- 可以快速反馈；
- 不产生幂等键/批次；
- 不改变库存；
- 可以重复调用。

执行是写操作：

- 必须幂等；
- 必须全有或全无；
- 必须写流水/审计；
- 必须处理并发。

两者不能共用一个“先预览再默默写入”的接口。

## 3. 预览流程

```text
POST /api/admin/stores/{id}/stock-adjustments/batch/preview
  -> 校验请求结构/行数
  -> 逐行：
       重复商品 -> DUPLICATE_BATCH_PRODUCT
       delta=0 -> INVALID_STOCK_DELTA
       商品不存在/门店不匹配 -> PRODUCT_NOT_FOUND
       available+delta<0 -> STOCK_INSUFFICIENT
       否则 valid，返回 before/after
  -> valid = errorCount == 0
  -> 不写任何表
```

## 4. 与执行的 TOCTOU

预览通过后，库存可能被订单或其他管理员改掉。执行时 B4 会重新校验并条件更新；预览结果不是承诺。

前端不能因为“预览通过”就跳过服务端错误处理。

## 5. 前端

管理台批量对话框：

- “校验预览”调用只读端点；
- 行级显示可用/错误码/预计库存；
- 文本变化清空上次预览；
- “提交批次”仍调用 B4；
- 错误显示 traceId。

## 6. 边界

未实现：

- CSV 文件上传；
- dry-run 与执行结果 diff；
- 部分成功；
- 预览保存/分享；
- 大量行性能；
- 预览与实际执行之间的库存锁定。
