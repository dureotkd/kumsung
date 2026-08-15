-- 관계 조회와 고객 포털의 주요 조회 경로를 인덱스로 보호한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_users_email_ci
    ON app_users(lower(email));

CREATE INDEX IF NOT EXISTS idx_quote_attachments_quote
    ON quote_attachments(quote_request_id, id);

CREATE INDEX IF NOT EXISTS idx_quote_documents_quote_created
    ON quote_documents(quote_request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_supplemental_quote_requested
    ON supplemental_requests(quote_request_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_logs_recipient_sent
    ON email_logs(lower(recipient), sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_notices_public
    ON notices(published, pinned DESC, created_at DESC);

-- SMTP 호출 중 DB 행 잠금을 유지하지 않도록 Outbox 작업을 먼저 소유권 주장한다.
ALTER TABLE email_outbox
    ADD COLUMN claimed_at TIMESTAMP,
    ADD COLUMN claimed_by VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_outbox_claim_recovery
    ON email_outbox(status, claimed_at)
    WHERE status = 'PROCESSING';

-- 업무 상태는 화면에서 사용하는 허용값만 저장한다.
UPDATE projects SET status='PLANNING'
WHERE status NOT IN ('PLANNING','IN_PROGRESS','ON_HOLD','COMPLETED','CANCELLED');
UPDATE contracts SET status='DRAFT'
WHERE status NOT IN ('DRAFT','REVIEWING','SIGNED','COMPLETED','CANCELLED');
UPDATE deliveries SET status='PREPARING'
WHERE status NOT IN ('PREPARING','SHIPPING','DELIVERED','CANCELLED');
UPDATE tax_invoices SET status='PENDING'
WHERE status NOT IN ('PENDING','ISSUED','PAID','CANCELLED');
UPDATE service_requests SET status='RECEIVED'
WHERE status NOT IN ('RECEIVED','REVIEWING','IN_PROGRESS','COMPLETED','CANCELLED');

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_status
    CHECK (status IN ('PLANNING','IN_PROGRESS','ON_HOLD','COMPLETED','CANCELLED'));

ALTER TABLE contracts
    ADD CONSTRAINT chk_contracts_status
    CHECK (status IN ('DRAFT','REVIEWING','SIGNED','COMPLETED','CANCELLED'));

ALTER TABLE deliveries
    ADD CONSTRAINT chk_deliveries_status
    CHECK (status IN ('PREPARING','SHIPPING','DELIVERED','CANCELLED'));

ALTER TABLE tax_invoices
    ADD CONSTRAINT chk_tax_invoices_status
    CHECK (status IN ('PENDING','ISSUED','PAID','CANCELLED'));

ALTER TABLE service_requests
    ADD CONSTRAINT chk_service_requests_status
    CHECK (status IN ('RECEIVED','REVIEWING','IN_PROGRESS','COMPLETED','CANCELLED'));
