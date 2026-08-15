CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_password_reset_active_user
    ON password_reset_tokens(user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_password_reset_token_active
    ON password_reset_tokens(token_hash,expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;
