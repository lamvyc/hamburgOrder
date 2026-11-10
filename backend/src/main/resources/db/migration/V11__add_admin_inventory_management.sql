-- Admin stock adjustments are not tied to an order.
ALTER TABLE stock_logs
    MODIFY order_id BIGINT NULL;

ALTER TABLE stock_logs
    ADD COLUMN operator_user_id BIGINT NULL AFTER order_id,
    ADD CONSTRAINT fk_stock_logs_operator
        FOREIGN KEY (operator_user_id) REFERENCES users (id);

-- Optimistic lock for admin product edits.
ALTER TABLE products
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER on_sale;

CREATE TABLE admin_audit_logs (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT      NOT NULL,
    action        VARCHAR(40) NOT NULL,
    target_type   VARCHAR(32) NOT NULL,
    target_id     BIGINT      NOT NULL,
    detail        JSON        NULL,
    created_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_actor_created (actor_user_id, created_at, id),
    KEY idx_admin_audit_target (target_type, target_id, id),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
