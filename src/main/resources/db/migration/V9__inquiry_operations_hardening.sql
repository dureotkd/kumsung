ALTER TABLE support_inquiries
    ADD COLUMN submission_key UUID,
    ADD COLUMN assigned_to VARCHAR(120),
    ADD COLUMN internal_note TEXT,
    ADD COLUMN answered_by VARCHAR(120),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE shop_inquiries
    ADD COLUMN submission_key UUID;

UPDATE support_inquiries SET status='RECEIVED'
WHERE status NOT IN ('RECEIVED','REVIEWING','ANSWERED','CLOSED','CANCELLED');
UPDATE shop_inquiries SET status='RECEIVED'
WHERE status NOT IN ('RECEIVED','REVIEWING','CONTACTED','COMPLETED','CANCELLED');

ALTER TABLE support_inquiries
    ADD CONSTRAINT chk_support_inquiries_status
    CHECK (status IN ('RECEIVED','REVIEWING','ANSWERED','CLOSED','CANCELLED'));
ALTER TABLE shop_inquiries
    ADD CONSTRAINT chk_shop_inquiries_status
    CHECK (status IN ('RECEIVED','REVIEWING','CONTACTED','COMPLETED','CANCELLED'));
ALTER TABLE sheet_outbox
    ADD CONSTRAINT chk_sheet_outbox_status
    CHECK (status IN ('PENDING','PROCESSING','RETRY','SENT','FAILED'));

CREATE UNIQUE INDEX uq_support_submission_key
    ON support_inquiries(submission_key) WHERE submission_key IS NOT NULL;
CREATE UNIQUE INDEX uq_shop_submission_key
    ON shop_inquiries(submission_key) WHERE submission_key IS NOT NULL;

CREATE TABLE support_inquiry_history (
    id BIGSERIAL PRIMARY KEY,
    support_inquiry_id BIGINT NOT NULL REFERENCES support_inquiries(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    note TEXT,
    changed_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('RECEIVED','REVIEWING','ANSWERED','CLOSED','CANCELLED'))
);

CREATE TABLE shop_inquiry_history (
    id BIGSERIAL PRIMARY KEY,
    shop_inquiry_id BIGINT NOT NULL REFERENCES shop_inquiries(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    note TEXT,
    changed_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('RECEIVED','REVIEWING','CONTACTED','COMPLETED','CANCELLED'))
);

CREATE INDEX idx_support_history_inquiry ON support_inquiry_history(support_inquiry_id,created_at DESC,id DESC);
CREATE INDEX idx_shop_history_inquiry ON shop_inquiry_history(shop_inquiry_id,created_at DESC,id DESC);
CREATE INDEX idx_support_status_created ON support_inquiries(status,created_at DESC,id DESC);
CREATE INDEX idx_shop_status_created ON shop_inquiries(status,created_at DESC,id DESC);
