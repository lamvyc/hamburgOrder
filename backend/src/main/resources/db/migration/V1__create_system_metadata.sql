-- Stage 0 deliberately creates only the smallest table needed to prove that
-- Flyway ran and that application SQL can read a known, migrated row.
CREATE TABLE system_metadata (
    meta_key   VARCHAR(64)  NOT NULL,
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (meta_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO system_metadata (meta_key, meta_value)
VALUES ('schema_ready', '1');
