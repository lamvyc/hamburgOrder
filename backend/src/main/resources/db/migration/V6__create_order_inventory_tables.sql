CREATE TABLE orders (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32)   NOT NULL,
    user_id      BIGINT        NOT NULL,
    store_id     BIGINT        NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    currency     CHAR(3)       NOT NULL DEFAULT 'CNY',
    total_amount DECIMAL(10, 2) NOT NULL,
    expires_at   DATETIME(3)   NOT NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_no (order_no),
    KEY idx_orders_user_created (user_id, created_at, id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT ck_orders_status CHECK (
        status IN ('WAIT_PAY', 'WAIT_MAKE', 'MAKING', 'READY', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_code VARCHAR(32)   NOT NULL,
    product_name VARCHAR(80)   NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL,
    quantity     INT           NOT NULL,
    line_amount  DECIMAL(10, 2) NOT NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_items_product (order_id, product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_line_amount CHECK (line_amount > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_events (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    order_id         BIGINT      NOT NULL,
    event_type       VARCHAR(32) NOT NULL,
    from_status      VARCHAR(16) NULL,
    to_status        VARCHAR(16) NOT NULL,
    operator_user_id BIGINT      NULL,
    reason           VARCHAR(255) NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_events_order (order_id, id),
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_events_operator FOREIGN KEY (operator_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock (
    store_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    available  INT         NOT NULL,
    reserved   INT         NOT NULL DEFAULT 0,
    version    INT         NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (store_id, product_id),
    CONSTRAINT fk_stock_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_available CHECK (available >= 0),
    CONSTRAINT ck_stock_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_stock_version CHECK (version >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock_logs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    order_id        BIGINT      NOT NULL,
    store_id        BIGINT      NOT NULL,
    product_id      BIGINT      NOT NULL,
    change_type     VARCHAR(16) NOT NULL,
    available_delta INT         NOT NULL,
    reserved_delta  INT         NOT NULL,
    available_after INT         NOT NULL,
    reserved_after  INT         NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stock_logs_order (order_id, id),
    CONSTRAINT fk_stock_logs_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_stock_logs_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_stock_logs_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_logs_type CHECK (change_type IN ('RESERVE', 'RELEASE', 'ADJUST')),
    CONSTRAINT ck_stock_logs_after CHECK (available_after >= 0 AND reserved_after >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_records (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    operation       VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    status          VARCHAR(16) NOT NULL,
    order_id        BIGINT      NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at    DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_scope (user_id, operation, idempotency_key),
    KEY idx_idempotency_order (order_id),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_idempotency_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
