CREATE TABLE shop_orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    product_id BIGINT REFERENCES shop_products(id) ON DELETE SET NULL,
    product_code VARCHAR(40) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    amount BIGINT NOT NULL CHECK (amount > 0),
    buyer_name VARCHAR(100) NOT NULL,
    buyer_email VARCHAR(120) NOT NULL,
    buyer_phone VARCHAR(30) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY'
        CHECK (status IN ('READY','PAID','FAILED','CANCELED')),
    payment_key VARCHAR(200) UNIQUE,
    payment_method VARCHAR(50),
    receipt_url VARCHAR(500),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shop_orders_created ON shop_orders(created_at DESC,id DESC);
CREATE INDEX idx_shop_orders_status ON shop_orders(status,created_at DESC,id DESC);
CREATE INDEX idx_shop_orders_customer ON shop_orders(customer_user_id,created_at DESC,id DESC);

-- 토스페이먼츠 공식 문서에서 제공하는 공용 테스트 상점 키입니다.
-- 실제 결제 전환 시 SHOP 관리자에서 라이브 키로 교체해야 합니다.
UPDATE shop_payment_settings
SET enabled=TRUE,
    mode='TEST',
    client_key='test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq',
    updated_by='SYSTEM_TEST_SETUP',
    updated_at=CURRENT_TIMESTAMP
WHERE id=1 AND client_key IS NULL;
