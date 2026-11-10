CREATE TABLE admin_stock_batches (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    admin_user_id   BIGINT      NOT NULL,
    store_id        BIGINT      NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    status          VARCHAR(16) NOT NULL,
    line_count      INT         NOT NULL,
    created_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_stock_batches_key (admin_user_id, idempotency_key),
    KEY idx_admin_stock_batches_store (store_id, id),
    CONSTRAINT fk_admin_stock_batches_admin FOREIGN KEY (admin_user_id) REFERENCES users (id),
    CONSTRAINT fk_admin_stock_batches_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT ck_admin_stock_batches_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_admin_stock_batches_lines CHECK (line_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE admin_stock_batch_lines (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    batch_id             BIGINT      NOT NULL,
    product_id           BIGINT      NOT NULL,
    delta                INT         NOT NULL,
    reason               VARCHAR(200) NOT NULL,
    available_before     INT         NOT NULL,
    available_after      INT         NOT NULL,
    stock_version_after  INT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_stock_batch_lines_product (batch_id, product_id),
    KEY idx_admin_stock_batch_lines_product (product_id, id),
    CONSTRAINT fk_admin_stock_batch_lines_batch FOREIGN KEY (batch_id) REFERENCES admin_stock_batches (id),
    CONSTRAINT fk_admin_stock_batch_lines_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_admin_stock_batch_lines_delta CHECK (delta <> 0),
    CONSTRAINT ck_admin_stock_batch_lines_before CHECK (available_before >= 0),
    CONSTRAINT ck_admin_stock_batch_lines_after CHECK (available_after >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
