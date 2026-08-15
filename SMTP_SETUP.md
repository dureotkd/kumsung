# 회사 도메인 SMTP 설정 및 보안 가이드

운영 발신 계정은 개인 Gmail·Daum/Hanmail 계정을 사용하지 않고, 홈페이지 도메인과 연결된 회사 메일 SMTP를 사용합니다. 수신자가 Gmail, Naver, Daum 또는 다른 회사 메일을 사용하더라도 회사 SMTP 서버가 외부 발송을 허용하면 정상적으로 수신할 수 있습니다.

## 도메인 등록 후 먼저 준비할 것

홈페이지 도메인을 등록하는 것만으로 메일함이나 SMTP 서버가 자동 생성되지는 않습니다. 도메인 메일 호스팅 또는 트랜잭션 메일 서비스에 가입한 뒤 다음 정보를 발급받아야 합니다.

- 발신 계정: 예) `no-reply@회사도메인`
- 견적 수신 계정: 예) `estimate@회사도메인`
- SMTP 호스트와 포트
- 보안방식: STARTTLS 또는 SSL
- SMTP 로그인 계정과 비밀번호 또는 앱 비밀번호
- DNS에 등록할 SPF, DKIM, DMARC 값

메일 서비스 가입과 DNS 인증이 완료되기 전까지 데모는 Mailpit을 유지합니다.

## 비밀번호 보관 방식

- SMTP 비밀번호는 `.env`에 직접 저장하지 않습니다.
- `scripts/configure-smtp.ps1`가 `.secrets/mail_password`에 저장하고 현재 사용자, SYSTEM, Administrators만 접근하도록 파일 권한을 제한합니다.
- `.env*`와 `.secrets/*`는 Git에서 제외됩니다.
- 컨테이너 설정에는 비밀번호가 아니라 `/run/secrets/mail_password` 경로만 전달됩니다.
- SSL/STARTTLS 연결은 SMTP 서버 인증서의 호스트 이름을 검증합니다.
- 관리자 화면과 애플리케이션 로그에는 비밀번호를 출력하지 않습니다.
- 담당자 변경이나 유출 의심 시 SMTP 비밀번호를 즉시 교체합니다.

서버 관리자 또는 Docker 권한 보유자는 컨테이너와 비밀 파일에 접근할 수 있으므로 최소 권한 운영이 필요합니다. 저장소, 메신저, 문서, 화면 캡처에 비밀번호를 남기지 마세요.

## 회사 도메인 SMTP 설정

메일 업체에서 받은 값으로 다음 명령을 실행합니다.

```powershell
cd C:\Kumsung_ENC_Smart_Platform
.\scripts\configure-smtp.ps1 `
  -Environment production `
  -Provider company `
  -Email no-reply@your-company-domain.example `
  -HostName smtp.your-mail-provider.example `
  -Port 587 `
  -Security starttls `
  -From no-reply@your-company-domain.example `
  -QuoteRecipient estimate@your-company-domain.example `
  -SupportRecipient b2b@your-company-domain.example `
  -SupportEmail b2b@your-company-domain.example `
  -Restart
```

`your-company-domain.example`과 SMTP 호스트는 실제 계약한 도메인·메일 업체 값으로 교체합니다. SSL 전용 포트 465를 사용하는 업체는 `-Port 465 -Security ssl`을 지정합니다. 인증 없는 SMTP 릴레이는 신뢰된 사내망에서만 `-NoAuth`로 사용하세요.

### 고객센터와 B2B 메일함을 하나로 사용하는 경우

고객문의 알림 수신, 관리자 답변 발신, 홈페이지에 표시할 회신 주소를 동일한 B2B 메일함으로 통합할 수 있습니다. 이 경우 `-Email`, `-From`, `-SupportRecipient`, `-SupportEmail`에 동일한 주소를 입력합니다. 단, 해당 주소가 실제 SMTP 로그인·발신이 허용된 메일함이어야 하며 대소문자는 표시상 차이만 있으므로 운영 설정에서는 소문자 사용을 권장합니다.

## 데모를 Mailpit으로 유지하기

```powershell
.\scripts\configure-smtp.ps1 -Environment demo -Provider mailpit -Restart
```

Mailpit은 `http://localhost:8025`에서 확인합니다. 회사 SMTP를 운영 환경에 연결하면 실제 수신자 메일함으로 전달되며 Mailpit에는 나타나지 않습니다.

## 발송 확인

1. 관리자 계정으로 로그인합니다.
2. **메일 설정**에서 공급자 `CUSTOM`, 서버, SSL/STARTTLS, 비밀번호 설정 여부를 확인합니다.
3. 테스트 수신 주소로 SMTP 테스트 메일을 보냅니다.
4. 회원가입 인증과 비밀번호 재설정 메일을 시험합니다.
5. Outbox 상태와 Gmail·Naver·Daum 수신함 및 스팸함을 확인합니다.

Outbox 상태는 `PENDING`, `PROCESSING`, `SENT`, `FAILED`입니다. `SENT`는 SMTP 서버가 수락했다는 뜻이며 최종 받은편지함 배달까지 보장하지는 않습니다.

## 주요 설정

| 설정 | 설명 |
|---|---|
| `MAIL_PROVIDER` | 운영은 `CUSTOM`, 데모는 `MAILPIT` |
| `MAIL_HOST`, `MAIL_PORT` | 메일 호스팅 업체가 제공한 SMTP 서버와 포트 |
| `MAIL_USERNAME` | 회사 도메인 SMTP 로그인 계정 |
| `MAIL_PASSWORD_FILE` | 컨테이너 내부 비밀번호 파일 경로 |
| `MAIL_PASSWORD_SECRET_FILE` | 호스트의 비밀번호 파일 경로(운영 Compose) |
| `MAIL_FROM` | 고객에게 표시할, 발신 인증된 회사 주소 |
| `MAIL_AUTH` | SMTP 인증 사용 여부 |
| `MAIL_STARTTLS`, `MAIL_STARTTLS_REQUIRED` | STARTTLS 사용 및 강제 여부 |
| `MAIL_SSL` | SSL 연결 사용 여부 |
| `QUOTE_RECIPIENT` | 새 견적 접수 알림을 받을 회사 내부 주소 |
| `SUPPORT_RECIPIENT` | 새 고객문의 알림을 실제로 받을 회사 내부 메일함(`b2b@`) |
| `SUPPORT_EMAIL` | 홈페이지와 고객 답변에 표시할 고객센터 별칭(`support@`) |

## 운영 전 확인

- 회사 도메인의 SPF·DKIM 인증 통과
- DMARC 정책과 보고 수신 주소 설정
- `MAIL_FROM` 주소가 메일 업체에서 발신 허용된 주소인지 확인
- 회원가입 인증, 비밀번호 재설정, 견적 접수, 관리자 초대 메일 시험
- 관리자 화면에서 Outbox가 `SENT`인지 확인
