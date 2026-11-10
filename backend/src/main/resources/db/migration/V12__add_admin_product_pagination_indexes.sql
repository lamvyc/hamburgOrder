-- Admin keyset pages: store + id, and store + on_sale + id.
CREATE INDEX idx_products_store_id
    ON products (store_id, id);

CREATE INDEX idx_products_store_onsale_id
    ON products (store_id, on_sale, id);
