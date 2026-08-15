ALTER TABLE quote_requests
    ADD COLUMN customer_webhard_url VARCHAR(500),
    ADD COLUMN estimate_amount NUMERIC(18,2),
    ADD COLUMN estimate_notes TEXT;

ALTER TABLE quote_documents
    ADD COLUMN contract_decision VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN contract_decision_note VARCHAR(1000),
    ADD COLUMN contract_decided_at TIMESTAMP,
    ADD COLUMN contract_decided_by BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    ADD COLUMN contract_decision_ip VARCHAR(64);

ALTER TABLE projects ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE contracts ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE deliveries ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE tax_invoices ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE service_requests ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE support_inquiries ADD COLUMN customer_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;

UPDATE projects x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);
UPDATE contracts x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);
UPDATE deliveries x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);
UPDATE tax_invoices x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);
UPDATE service_requests x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);
UPDATE support_inquiries x SET customer_user_id=u.id FROM app_users u
WHERE x.customer_user_id IS NULL AND u.role='CUSTOMER' AND lower(x.customer_email)=lower(u.email);

CREATE INDEX idx_projects_owner ON projects(customer_user_id, created_at DESC);
CREATE INDEX idx_contracts_owner ON contracts(customer_user_id, created_at DESC);
CREATE INDEX idx_deliveries_owner ON deliveries(customer_user_id, created_at DESC);
CREATE INDEX idx_tax_invoices_owner ON tax_invoices(customer_user_id, created_at DESC);
CREATE INDEX idx_service_requests_owner ON service_requests(customer_user_id, created_at DESC);
CREATE INDEX idx_support_inquiries_owner ON support_inquiries(customer_user_id, created_at DESC);
