CREATE TABLE coupon_templates (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(32)  NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    discount_amount   DECIMAL(10, 2) NOT NULL,
    min_order_amount  DECIMAL(10, 2) NOT NULL,
    total_quantity    INT          NOT NULL,
    claimed_quantity  INT          NOT NULL DEFAULT 0,
    per_user_limit    INT          NOT NULL DEFAULT 1,
    valid_from        DATETIME(3)  NOT NULL,
    valid_to          DATETIME(3)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_templates_code (code),
    KEY idx_coupon_templates_status_valid (status, valid_from, valid_to),
    CONSTRAINT ck_coupon_templates_discount CHECK (discount_amount > 0),
    CONSTRAINT ck_coupon_templates_min CHECK (min_order_amount >= 0),
    CONSTRAINT ck_coupon_templates_total CHECK (total_quantity >= 0),
    CONSTRAINT ck_coupon_templates_claimed CHECK (claimed_quantity >= 0),
    CONSTRAINT ck_coupon_templates_quantity CHECK (claimed_quantity <= total_quantity),
    CONSTRAINT ck_coupon_templates_per_user CHECK (per_user_limit = 1),
    CONSTRAINT ck_coupon_templates_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_coupon_templates_validity CHECK (valid_from < valid_to)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_coupons (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    template_id  BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'UNUSED',
    order_id     BIGINT      NULL,
    claimed_at   DATETIME(3) NOT NULL,
    used_at      DATETIME(3) NULL,
    version      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupons_template_user (template_id, user_id),
    KEY idx_user_coupons_user_status (user_id, status, id),
    KEY idx_user_coupons_order (order_id),
    CONSTRAINT fk_user_coupons_template FOREIGN KEY (template_id) REFERENCES coupon_templates (id),
    CONSTRAINT fk_user_coupons_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_coupons_status CHECK (status IN ('UNUSED', 'LOCKED', 'USED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE orders
    ADD COLUMN coupon_id BIGINT NULL AFTER store_id,
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER total_amount;

INSERT INTO coupon_templates
    (code, name, discount_amount, min_order_amount, total_quantity, claimed_quantity,
     per_user_limit, valid_from, valid_to, status, version)
VALUES
    ('WELCOME5', '新人立减 5 元', 5.00, 30.00, 100, 0, 1,
     '2026-01-01 00:00:00.000', '2027-12-31 23:59:59.000', 'ACTIVE', 0),
    ('LATE10', '满 60 减 10', 10.00, 60.00, 50, 0, 1,
     '2026-01-01 00:00:00.000', '2027-12-31 23:59:59.000', 'ACTIVE', 0);
