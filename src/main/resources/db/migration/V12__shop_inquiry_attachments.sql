CREATE TABLE shop_inquiry_attachments (
    id BIGSERIAL PRIMARY KEY,
    shop_inquiry_id BIGINT NOT NULL REFERENCES shop_inquiries(id) ON DELETE CASCADE,
    shop_inquiry_item_id BIGINT NOT NULL REFERENCES shop_inquiry_items(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shop_attachments_inquiry ON shop_inquiry_attachments(shop_inquiry_id,id);
CREATE INDEX idx_shop_attachments_item ON shop_inquiry_attachments(shop_inquiry_item_id,id);
