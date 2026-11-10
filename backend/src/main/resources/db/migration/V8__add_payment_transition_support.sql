-- Payment commits the reservation: reserved decreases, available is not
-- decremented a second time. Add the matching stock log type.
ALTER TABLE stock_logs
    DROP CHECK ck_stock_logs_type;

ALTER TABLE stock_logs
    ADD CONSTRAINT ck_stock_logs_type
        CHECK (change_type IN ('RESERVE', 'COMMIT', 'RELEASE', 'ADJUST'));

-- Staff workbench lists one store's active orders by creation time.
CREATE INDEX idx_orders_store_status_created
    ON orders (store_id, status, created_at, id);
