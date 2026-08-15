ALTER TABLE quote_requests
    ADD COLUMN updated_at TIMESTAMP,
    ADD COLUMN assigned_to VARCHAR(120),
    ADD COLUMN webhard_url VARCHAR(500);

UPDATE quote_requests SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE quote_requests ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE quote_requests ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(60) NOT NULL,
    company_name VARCHAR(150),
    phone VARCHAR(30),
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE quote_status_history (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    note VARCHAR(500),
    changed_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO quote_status_history(quote_request_id, status, note, changed_by, created_at)
SELECT id, status, '온라인 견적 접수', email, created_at FROM quote_requests;

CREATE TABLE supplemental_requests (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    request_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    requested_by VARCHAR(120),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE quote_documents (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    document_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE quote_messages (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT NOT NULL REFERENCES quote_requests(id) ON DELETE CASCADE,
    sender_email VARCHAR(120) NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_logs (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT REFERENCES quote_requests(id) ON DELETE SET NULL,
    recipient VARCHAR(120) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    quote_request_id BIGINT REFERENCES quote_requests(id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
    start_date DATE,
    end_date DATE,
    progress INTEGER NOT NULL DEFAULT 0 CHECK(progress BETWEEN 0 AND 100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE contracts (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    contract_number VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    amount NUMERIC(18,2),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    contract_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE deliveries (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    item_name VARCHAR(200) NOT NULL,
    quantity VARCHAR(60),
    expected_date DATE,
    delivered_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'PREPARING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tax_invoices (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    contract_id BIGINT REFERENCES contracts(id) ON DELETE SET NULL,
    issue_number VARCHAR(60) NOT NULL UNIQUE,
    amount NUMERIC(18,2) NOT NULL,
    issued_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE service_requests (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notices (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE support_inquiries (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(120) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    answer TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMP
);

CREATE INDEX idx_quote_customer_email ON quote_requests(lower(email));
CREATE INDEX idx_history_quote ON quote_status_history(quote_request_id, created_at);
CREATE INDEX idx_messages_quote ON quote_messages(quote_request_id, created_at);
CREATE INDEX idx_projects_customer ON projects(lower(customer_email));
CREATE INDEX idx_contracts_customer ON contracts(lower(customer_email));
