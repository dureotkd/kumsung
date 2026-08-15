ALTER TABLE app_users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verified_at TIMESTAMP;

ALTER TABLE quote_requests
    ADD COLUMN owner_user_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;

ALTER TABLE quote_documents
    ADD COLUMN content_sha256 VARCHAR(64),
    ADD COLUMN approved_by_email VARCHAR(120),
    ADD COLUMN approved_ip VARCHAR(64),
    ADD COLUMN approval_user_agent VARCHAR(500),
    ADD COLUMN approval_version INTEGER NOT NULL DEFAULT 1;

-- 이전 개발 버전의 알려진 기본 관리자 계정은 운영 마이그레이션 시 비활성화한다.
UPDATE app_users SET enabled=FALSE
WHERE role='ADMIN' AND email='admin@kumsungenc.co.kr' AND email_verified=FALSE;

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE privacy_consents (
    id BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(30) NOT NULL,
    subject_id BIGINT NOT NULL,
    email VARCHAR(120) NOT NULL,
    consent_version VARCHAR(30) NOT NULL,
    purpose VARCHAR(300) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    agreed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_outbox (
    id BIGSERIAL PRIMARY KEY,
    quote_request_id BIGINT REFERENCES quote_requests(id) ON DELETE SET NULL,
    recipient VARCHAR(120) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_email VARCHAR(120),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quote_owner ON quote_requests(owner_user_id, created_at DESC);
CREATE INDEX idx_verification_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX idx_outbox_pending ON email_outbox(status, next_attempt_at, id);
CREATE INDEX idx_privacy_subject ON privacy_consents(subject_type, subject_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
