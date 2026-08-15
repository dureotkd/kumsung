CREATE TABLE shop_products (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO shop_products(code,name,description,display_order) VALUES
('DRY_PD','건식PD','설치 환경과 도면에 맞춘 주문 제작',10),
('FIRE_HYDRANT_BOX','소화전함','현장 규격별 설계·제작',20),
('WATERPROOF_EQUIPMENT_BOX','방수기구함','용도와 치수별 맞춤 제작',30),
('SEISMIC_FRAME','내진다이','도면 기반 내진 사양 검토',40)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE shop_inquiries (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(40) NOT NULL UNIQUE,
    customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    company_name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    admin_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shop_inquiry_items (
    id BIGSERIAL PRIMARY KEY,
    shop_inquiry_id BIGINT NOT NULL REFERENCES shop_inquiries(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES shop_products(id) ON DELETE SET NULL,
    product_code VARCHAR(40) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 999),
    specifications VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE support_inquiries
    ADD COLUMN company_name VARCHAR(150),
    ADD COLUMN contact_name VARCHAR(100),
    ADD COLUMN phone VARCHAR(30),
    ADD COLUMN receipt_number VARCHAR(40),
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'PORTAL';

CREATE UNIQUE INDEX idx_support_receipt ON support_inquiries(receipt_number) WHERE receipt_number IS NOT NULL;

CREATE TABLE sheet_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    claimed_at TIMESTAMP,
    claimed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE INDEX idx_shop_inquiries_created ON shop_inquiries(created_at DESC,id DESC);
CREATE INDEX idx_shop_inquiries_owner ON shop_inquiries(customer_user_id,created_at DESC,id DESC);
CREATE INDEX idx_shop_items_inquiry ON shop_inquiry_items(shop_inquiry_id,id);
CREATE INDEX idx_sheet_outbox_pending ON sheet_outbox(status,next_attempt_at,id);

