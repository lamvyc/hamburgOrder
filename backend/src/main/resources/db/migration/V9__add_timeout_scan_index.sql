-- The A5 timeout scanner selects WAIT_PAY orders by expiry.
CREATE INDEX idx_orders_status_expires
    ON orders (status, expires_at, id);
