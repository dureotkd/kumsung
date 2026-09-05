CREATE TABLE oauth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oauth_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_oauth_identity_provider CHECK (provider IN ('NAVER'))
);

CREATE INDEX idx_oauth_identity_user ON oauth_identities(user_id);
