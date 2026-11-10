-- A1 adds only the read model needed by store selection and menu browsing.
-- Product stock and order tables are intentionally deferred to A3.
CREATE TABLE stores (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32)  NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    latitude        DECIMAL(9, 6) NOT NULL,
    longitude       DECIMAL(9, 6) NOT NULL,
    business_status VARCHAR(16)  NOT NULL,
    accepting_orders TINYINT      NOT NULL DEFAULT 1,
    opening_hours   VARCHAR(64)  NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stores_code (code),
    CONSTRAINT ck_stores_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_stores_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_stores_business_status CHECK (business_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_stores_accepting_orders CHECK (accepting_orders IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    store_id      BIGINT        NOT NULL,
    product_code  VARCHAR(32)   NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    description   VARCHAR(255)  NOT NULL DEFAULT '',
    category_code VARCHAR(32)   NOT NULL,
    category_name VARCHAR(32)   NOT NULL,
    category_sort SMALLINT      NOT NULL,
    price         DECIMAL(10, 2) NOT NULL,
    on_sale       TINYINT       NOT NULL DEFAULT 1,
    sort_order    INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_store_code (store_id, product_code),
    KEY idx_products_store_menu (store_id, on_sale, category_sort, sort_order, id),
    CONSTRAINT fk_products_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_on_sale CHECK (on_sale IN (0, 1)),
    CONSTRAINT ck_products_category_sort CHECK (category_sort >= 0),
    CONSTRAINT ck_products_sort_order CHECK (sort_order >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
