ALTER TABLE app_users
    ADD COLUMN admin_role VARCHAR(20);

UPDATE app_users
SET admin_role = 'SUPER_ADMIN'
WHERE role = 'ADMIN';

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_admin_role
    CHECK (
        (role = 'ADMIN' AND admin_role IN ('SUPER_ADMIN', 'ADMIN'))
        OR
        (role <> 'ADMIN' AND admin_role IS NULL)
    );

CREATE TABLE admin_account_tokens (
    id BIGSERIAL PRIMARY KEY,
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('INVITE', 'PASSWORD_RESET')),
    user_id BIGINT REFERENCES app_users(id) ON DELETE CASCADE,
    email VARCHAR(120) NOT NULL,
    name VARCHAR(60) NOT NULL,
    admin_role VARCHAR(20) NOT NULL CHECK (admin_role IN ('SUPER_ADMIN', 'ADMIN')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    invited_by VARCHAR(120) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (purpose = 'INVITE' AND user_id IS NULL)
        OR
        (purpose = 'PASSWORD_RESET' AND user_id IS NOT NULL)
    )
);

CREATE INDEX idx_admin_tokens_active
    ON admin_account_tokens(token_hash, expires_at)
    WHERE used_at IS NULL AND revoked_at IS NULL;

CREATE INDEX idx_admin_tokens_email
    ON admin_account_tokens(lower(email), created_at DESC);
