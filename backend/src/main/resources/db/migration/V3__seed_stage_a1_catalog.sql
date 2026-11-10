-- Demo data for local development and integration tests.
-- Explicit ids make the HTTP contract and screenshots reproducible.
INSERT INTO stores (id, code, name, address, latitude, longitude,
                    business_status, accepting_orders, opening_hours)
VALUES (1, 'CENTRAL', '中央车站店', '静安区南京西路 88 号',
        31.230400, 121.473700, 'OPEN', 1, '10:00–22:00'),
       (2, 'RIVERSIDE', '滨江店', '浦东新区滨江大道 66 号',
        31.239700, 121.499800, 'OPEN', 0, '11:00–23:00'),
       (3, 'OLD_TOWN', '老城店', '黄浦区方浜中路 21 号',
        31.227100, 121.492000, 'CLOSED', 0, '09:00–21:00');

INSERT INTO products (id, store_id, product_code, name, description,
                     category_code, category_name, category_sort,
                     price, on_sale, sort_order)
VALUES
    (1001, 1, 'S1-CLASSIC', '经典牛肉堡',
     '手打牛肉饼、切达芝士、酸黄瓜与招牌酱', 'BURGER', '汉堡', 10, 32.00, 1, 10),
    (1002, 1, 'S1-CHEESE', '芝士双层堡',
     '双层牛肉饼与双份切达芝士', 'BURGER', '汉堡', 10, 42.00, 1, 20),
    (1003, 1, 'S1-CHICKEN', '脆鸡堡',
     '香脆鸡腿排、生菜与蜂蜜芥末酱', 'BURGER', '汉堡', 10, 30.00, 1, 30),
    (1004, 1, 'S1-TRUFFLE', '松露薯条',
     '细切薯条、松露盐与帕玛森碎', 'SIDE', '小食', 20, 22.00, 1, 10),
    (1005, 1, 'S1-WINGS', '烟熏鸡翅',
     '烟熏辣椒酱与青柠', 'SIDE', '小食', 20, 26.00, 1, 20),
    (1006, 1, 'S1-COLA', '冰镇可乐',
     '玻璃瓶装，冰镇', 'DRINK', '饮品', 30, 10.00, 1, 10),
    (1007, 1, 'S1-SHAKE', '海盐奶昔',
     '香草奶昔与海盐焦糖', 'DRINK', '饮品', 30, 24.00, 0, 20),

    (2001, 2, 'S2-CLASSIC', '经典牛肉堡',
     '手打牛肉饼、切达芝士、酸黄瓜与招牌酱', 'BURGER', '汉堡', 10, 32.00, 1, 10),
    (2002, 2, 'S2-BACON', '培根芝士堡',
     '烟熏培根、双层芝士与焦糖洋葱', 'BURGER', '汉堡', 10, 40.00, 1, 20),
    (2003, 2, 'S2-ONION', '洋葱圈',
     '厚切洋葱与牧场蘸酱', 'SIDE', '小食', 20, 18.00, 1, 10),
    (2004, 2, 'S2-FRIES', '海盐薯条',
     '细切薯条与海盐', 'SIDE', '小食', 20, 15.00, 1, 20),
    (2005, 2, 'S2-LEMONADE', '柠檬气泡水',
     '鲜柠檬、薄荷与气泡水', 'DRINK', '饮品', 30, 16.00, 1, 10),
    (2006, 2, 'S2-COFFEE', '冷萃咖啡',
     '低温慢萃咖啡', 'DRINK', '饮品', 30, 18.00, 1, 20),

    (3001, 3, 'S3-CLASSIC', '经典牛肉堡',
     '手打牛肉饼、切达芝士、酸黄瓜与招牌酱', 'BURGER', '汉堡', 10, 30.00, 1, 10),
    (3002, 3, 'S3-MUSHROOM', '蘑菇堡',
     '炒蘑菇、瑞士芝士与蒜香蛋黄酱', 'BURGER', '汉堡', 10, 36.00, 1, 20),
    (3003, 3, 'S3-SALAD', '田园沙拉',
     '生菜、樱桃番茄与油醋汁', 'SIDE', '小食', 20, 20.00, 1, 10),
    (3004, 3, 'S3-TEA', '乌龙茶',
     '冷泡乌龙茶', 'DRINK', '饮品', 30, 12.00, 1, 10),
    (3005, 3, 'S3-PIE', '苹果派',
     '肉桂苹果与酥皮', 'DESSERT', '甜点', 40, 14.00, 0, 10);
