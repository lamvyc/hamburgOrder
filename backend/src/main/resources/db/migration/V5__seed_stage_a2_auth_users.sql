-- Demo accounts only. Password for all three is Burger@123.
-- BCrypt cost 12; plaintext is deliberately not stored.
INSERT INTO users (id, username, password_hash, display_name, role, enabled)
VALUES
    (1, 'demo_customer',
     '$2y$12$US6afS7B0yAIrLwogEtw1uX/q6Rl6wUCcyPLKGpku7CX/LF1xOiFK',
     '演示顾客', 'CUSTOMER', 1),
    (2, 'demo_staff',
     '$2y$12$8l78OzpqyAvrWkQEBjcZvO4GETSfTJ1h5GW7s7dqOS5T05olPAgny',
     '中央车站店员工', 'STAFF', 1),
    (3, 'demo_admin',
     '$2y$12$uFsREOEe1DVNJKPp2OM2Nuot9gf.LdqzkZkllo08OlkUA9cztIpB6',
     '演示管理员', 'ADMIN', 1);

INSERT INTO user_store_memberships (user_id, store_id)
VALUES (2, 1);
