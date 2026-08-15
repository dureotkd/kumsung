# 운영 배포 가이드

## 1. 사전 준비

- Linux 서버와 Docker Compose
- 서버로 연결된 실제 도메인
- 홈페이지 도메인과 연결된 회사 메일 호스팅의 SMTP 계정
- 발신 도메인의 SPF/DKIM/DMARC
- 실제 견적 담당 이메일
- 12자 이상의 강한 관리자 비밀번호
- 개인정보 처리방침 담당 이메일 검토

## 2. 비밀정보 설정

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

`.env.production`의 예시 값을 실제 값으로 교체합니다. `ADMIN_EMAIL`, `ADMIN_PASSWORD`는 최초 최고관리자 생성 때만 사용합니다. 이 파일은 Git에 포함되지 않습니다.

## 3. 배포

```bash
docker compose --env-file .env.production -f compose.prod.yml config
docker compose --env-file .env.production -f compose.prod.yml up -d --build
docker compose --env-file .env.production -f compose.prod.yml ps
```

확인 항목:

- `postgres`, `clamav`, `app`, `caddy`, `backup` 상태가 healthy/running
- `https://도메인/actuator/health` 응답이 `UP`
- 관리자 로그인
- 테스트 고객 이메일 인증
- 정상 PDF 접수와 위장 파일 차단
- 고객·담당자 이메일 수신
- 견적서 PDF·Excel 자동 생성 및 두 파일 다운로드
- 계약서 수락·거절 기록과 알림

## 4. 백업 확인

```bash
docker compose --env-file .env.production -f compose.prod.yml exec backup \
  sh /usr/local/bin/backup.sh
docker volume inspect kumsung-platform_backups_data
```

백업 볼륨을 동일 서버에만 두지 말고 별도 스토리지로 복제합니다. 분기마다 복구훈련을 수행합니다.

## 5. 관리자 계정

코드에 기본 관리자 계정은 없습니다. `ADMIN_EMAIL`, `ADMIN_PASSWORD`를 명시하면 최초 최고관리자만 생성됩니다. 첫 로그인을 확인한 즉시 두 값을 비우고 `docker compose up -d app`으로 컨테이너를 재생성합니다. 같은 이메일의 관리자가 이미 존재하는 경우 재시작해도 비밀번호를 덮어쓰지 않습니다. 이후 관리자는 관리자 화면의 **관리자 관리** 메뉴에서 이메일 초대로 추가합니다. 자세한 정책은 `ADMIN_MANAGEMENT.md`를 확인합니다.

## 6. 운영 보안

- PostgreSQL과 ClamAV 포트는 외부에 공개하지 않습니다.
- Caddy를 통하지 않고 애플리케이션 포트에 직접 접근할 수 없게 방화벽을 설정합니다.
- `.env.production`, 백업, 애플리케이션 로그의 접근권한을 제한합니다.
- ClamAV 시그니처 업데이트와 SMTP 실패 Outbox를 모니터링합니다.
- 관리자 화면에서 이메일 및 Google Sheets Outbox의 `FAILED` 건을 확인하고 원인 수정 후 재처리합니다.
- 고객문의·SMART SHOP 문의의 기본 보존기간은 3년이며 `INQUIRY_RETENTION_DAYS`로 회사 정책에 맞게 조정합니다.
- Google Sheet에 복제된 개인정보는 애플리케이션 DB와 별개이므로 Google Workspace 보존·삭제 정책을 함께 설정합니다.

## 7. 실제 고객 이메일 설정

홈페이지 도메인을 등록한 뒤 같은 도메인의 메일 호스팅 또는 트랜잭션 메일 서비스를 개통합니다. 도메인 등록만으로 SMTP가 자동 제공되는 것은 아니므로, 메일 업체에서 SMTP 호스트·포트·보안방식·로그인 계정·비밀번호를 발급받아야 합니다. 수신자의 Gmail·Naver·Daum 사용 여부와 관계없이 회사 SMTP 서버가 외부 발송을 허용하면 모두 수신할 수 있습니다. 비밀번호는 `.env.production`에 직접 입력하지 않고 `scripts/configure-smtp.ps1`가 생성하는 접근 제한 비밀 파일을 사용합니다.

```dotenv
MAIL_PROVIDER=CUSTOM
MAIL_HOST=smtp.your-mail-provider.example
MAIL_PORT=587
MAIL_USERNAME=no-reply@your-company-domain.example
MAIL_PASSWORD=
MAIL_PASSWORD_FILE=/run/secrets/mail_password
MAIL_PASSWORD_SECRET_FILE=./.secrets/mail_password
MAIL_FROM=no-reply@your-company-domain.example
QUOTE_RECIPIENT=estimate@your-company-domain.example
SUPPORT_RECIPIENT=b2b@your-company-domain.example
SUPPORT_EMAIL=support@your-company-domain.example
```

설정 명령 예시는 다음과 같습니다. 실제 값은 메일 호스팅 업체가 안내한 값을 사용합니다.

```powershell
.\scripts\configure-smtp.ps1 `
  -Environment production `
  -Provider company `
  -Email no-reply@your-company-domain.example `
  -HostName smtp.your-mail-provider.example `
  -Port 587 `
  -Security starttls `
  -From no-reply@your-company-domain.example `
  -QuoteRecipient estimate@your-company-domain.example `
  -Restart
```

발신 도메인의 SPF·DKIM·DMARC를 설정한 뒤 Gmail·Naver·Daum 등 외부 수신함으로 회원가입 인증메일, 비밀번호 재설정 메일과 견적 알림을 시험합니다.

## 8. 이번 배포 이후 수행

- 실제 트래픽 기준 부하 테스트
- 외부 취약점 진단과 침투 테스트
- 브라우저 호환성·접근성 점검
- 알림·대시보드 연동
