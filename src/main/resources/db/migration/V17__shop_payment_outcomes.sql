ALTER TABLE shop_orders
    ADD COLUMN canceled_at TIMESTAMP,
    ADD COLUMN cancel_reason VARCHAR(500);

CREATE INDEX idx_shop_orders_payment_outcome
    ON shop_orders(status,updated_at DESC,id DESC);
