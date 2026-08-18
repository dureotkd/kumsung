ALTER TABLE app_users
    DROP CONSTRAINT chk_app_users_admin_role;

ALTER TABLE app_users
    ADD CONSTRAINT chk_app_users_admin_role
    CHECK (
        (role = 'ADMIN' AND admin_role IN ('SUPER_ADMIN', 'ADMIN', 'SHOP_ADMIN'))
        OR
        (role <> 'ADMIN' AND admin_role IS NULL)
    );

ALTER TABLE admin_account_tokens
    DROP CONSTRAINT admin_account_tokens_admin_role_check;

ALTER TABLE admin_account_tokens
    ADD CONSTRAINT admin_account_tokens_admin_role_check
    CHECK (admin_role IN ('SUPER_ADMIN', 'ADMIN', 'SHOP_ADMIN'));

ALTER TABLE shop_products
    ADD COLUMN price BIGINT;

ALTER TABLE shop_products
    ADD CONSTRAINT chk_shop_products_price
    CHECK (price IS NULL OR price >= 0);

CREATE TABLE shop_payment_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mode VARCHAR(10) NOT NULL DEFAULT 'TEST' CHECK (mode IN ('TEST', 'LIVE')),
    client_key VARCHAR(200),
    updated_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO shop_payment_settings(id,enabled,mode)
VALUES (1,FALSE,'TEST')
ON CONFLICT (id) DO NOTHING;
