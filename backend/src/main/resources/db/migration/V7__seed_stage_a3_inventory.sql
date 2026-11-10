-- Second customer exists only to prove order ownership isolation.
INSERT INTO users (id, username, password_hash, display_name, role, enabled)
VALUES
    (4, 'demo_customer_two',
     '$2y$12$t/yJOSgLZo/0D9HjyxQ/du8/vTASGM3r6sml8nwJUUuvuo1H8gljS',
     '第二顾客', 'CUSTOMER', 1);

INSERT INTO stock (store_id, product_id, available, reserved, version)
VALUES
    (1, 1001, 50, 0, 0),
    (1, 1002, 30, 0, 0),
    (1, 1003, 40, 0, 0),
    (1, 1004, 60, 0, 0),
    (1, 1005, 40, 0, 0),
    (1, 1006, 100, 0, 0),
    (1, 1007, 0, 0, 0),

    (2, 2001, 40, 0, 0),
    (2, 2002, 30, 0, 0),
    (2, 2003, 40, 0, 0),
    (2, 2004, 50, 0, 0),
    (2, 2005, 60, 0, 0),
    (2, 2006, 40, 0, 0),

    (3, 3001, 20, 0, 0),
    (3, 3002, 20, 0, 0),
    (3, 3003, 30, 0, 0),
    (3, 3004, 40, 0, 0),
    (3, 3005, 0, 0, 0);
