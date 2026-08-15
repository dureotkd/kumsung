CREATE TABLE quote_requests (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(30) NOT NULL UNIQUE,
    company_name VARCHAR(150) NOT NULL,
    business_number VARCHAR(30),
    contact_name VARCHAR(60) NOT NULL,
    email VARCHAR(120) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    site_name VARCHAR(200),
    site_address VARCHAR(300),
    product_type VARCHAR(80) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    desired_date TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE quote_attachments (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL
);

CREATE INDEX idx_quote_requests_created_at ON quote_requests(created_at DESC);
CREATE INDEX idx_quote_requests_status ON quote_requests(status);
