-- 관리자 전체 목록과 상태별 조회가 데이터 증가 후에도 정렬 스캔으로 느려지지 않게 한다.
CREATE INDEX IF NOT EXISTS idx_app_users_role_created
    ON app_users(role, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_quotes_status_created
    ON quote_requests(status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_projects_created
    ON projects(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_contracts_created
    ON contracts(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_deliveries_created
    ON deliveries(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_tax_invoices_created
    ON tax_invoices(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_service_requests_created
    ON service_requests(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_support_inquiries_created
    ON support_inquiries(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_notices_created
    ON notices(created_at DESC, id DESC);

-- 관리자 견적 검색의 '%검색어%' 전체 테이블 스캔을 방지한다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_quotes_receipt_trgm
    ON quote_requests USING gin(receipt_number gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_quotes_company_trgm
    ON quote_requests USING gin(company_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_quotes_subject_trgm
    ON quote_requests USING gin(subject gin_trgm_ops);

-- 동시에 발급되는 인증/초대 토큰이 여러 개 활성 상태가 되는 경쟁 조건을 DB에서도 차단한다.
DELETE FROM email_verification_tokens older
USING email_verification_tokens newer
WHERE older.user_id=newer.user_id AND older.used_at IS NULL AND newer.used_at IS NULL AND older.id<newer.id;

UPDATE admin_account_tokens older
SET revoked_at=current_timestamp
FROM admin_account_tokens newer
WHERE older.purpose='INVITE' AND newer.purpose='INVITE'
  AND lower(older.email)=lower(newer.email)
  AND older.used_at IS NULL AND older.revoked_at IS NULL
  AND newer.used_at IS NULL AND newer.revoked_at IS NULL
  AND older.id<newer.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_email_verification_active_user
    ON email_verification_tokens(user_id)
    WHERE used_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_active_invite_email
    ON admin_account_tokens(lower(email))
    WHERE purpose='INVITE' AND used_at IS NULL AND revoked_at IS NULL;
