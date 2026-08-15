ALTER TABLE email_outbox
    ADD COLUMN reference_type VARCHAR(40),
    ADD COLUMN reference_id BIGINT;

CREATE INDEX idx_email_outbox_reference
    ON email_outbox(reference_type, reference_id, id DESC)
    WHERE reference_type IS NOT NULL AND reference_id IS NOT NULL;

