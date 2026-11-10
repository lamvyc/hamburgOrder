# 阶段 B1 数据库说明

## 1. V10 迁移

```text
V10__create_coupon_tables.sql
```

新增：

- `coupon_templates`
- `user_coupons`
- `orders.coupon_id`
- `orders.discount_amount`

没有修改 V1–V9。

## 2. coupon_templates

| 列 | 说明 |
| --- | --- |
| `code` | 唯一券码 |
| `discount_amount` | 固定优惠金额 |
| `min_order_amount` | 使用门槛 |
| `total_quantity` / `claimed_quantity` | 总量与已领量 |
| `per_user_limit` | 当前固定 1 |
| `valid_from` / `valid_to` | 有效期 |
| `status` | ACTIVE/INACTIVE |
| `version` | 领取/更新版本 |

约束：

```text
UNIQUE (code)
CHECK claimed_quantity >= 0
CHECK claimed_quantity <= total_quantity
CHECK discount_amount > 0
CHECK min_order_amount >= 0
CHECK per_user_limit = 1
CHECK valid_from < valid_to
```

领取 SQL：

```sql
UPDATE coupon_templates
SET claimed_quantity = claimed_quantity + 1,
    version = version + 1
WHERE id = ?
  AND status = 'ACTIVE'
  AND claimed_quantity < total_quantity
  AND valid_from <= ?
  AND valid_to >= ?;
```

实际 `EXPLAIN`：

```text
key=PRIMARY
rows=1
Extra=Using where
```

## 3. user_coupons

| 列 | 说明 |
| --- | --- |
| `template_id` / `user_id` | 所属模板与用户 |
| `status` | UNUSED / LOCKED / USED |
| `order_id` | 锁券后的订单 |
| `claimed_at` / `used_at` | 领取/支付时间 |
| `version` | 状态版本 |

索引：

```text
UNIQUE (template_id, user_id)
(user_id, status, id)
(order_id)
```

单用户限领 1 张由唯一键兜底；状态由条件 UPDATE 推进。

## 4. orders 扩展

```text
coupon_id BIGINT NULL
discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0
```

`total_amount` 是应付金额；商品小计 = `total_amount + discount_amount`。

A5/B1 旧订单默认 `discount_amount=0`，无需回填业务数据。

## 5. 状态流转

```text
claim:  INSERT UNUSED
order:  UPDATE UNUSED -> LOCKED (id,user,order)
pay:    UPDATE LOCKED -> USED (id,order)
cancel: UPDATE LOCKED -> UNUSED (id,order)
```

每一步都检查 affected rows = 1。

## 6. 实际数据

B1 E2E 订单 18：

```text
order_no=BO20261109124822B7B4BB
status=CANCELLED
total_amount=59.00
discount_amount=5.00
coupon_id=1
```

用户券最终：

```text
id=1 template_id=1 user_id=1 status=UNUSED order_id=NULL version=6
```

模板：

```text
WELCOME5 claimed_quantity=1 total_quantity=100
LATE10   claimed_quantity=0 total_quantity=50
```

库存：

```text
RESERVE available_delta=-2 reserved_delta=+2 available_after=39 reserved_after=6
RELEASE available_delta=+2 reserved_delta=-2 available_after=41 reserved_after=4
```

## 7. 验证

```bash
set -a; source .env; set +a
docker compose --env-file .env -f deploy/compose.yaml exec -T \
  -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e "SELECT id,code,claimed_quantity,total_quantity FROM coupon_templates;
      SELECT id,status,order_id FROM user_coupons;
      SELECT id,order_no,total_amount,discount_amount,coupon_id FROM orders WHERE coupon_id IS NOT NULL;"
```

## 8. 未建立

- 百分比/阶梯券；
- 券叠加/互斥；
- 券码兑换；
- 券过期状态；
- 发券批次、审计、作废；
- 优惠分摊到 order_items。
