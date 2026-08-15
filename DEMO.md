# ngrok + Nginx + Docker 시연 환경

이 구성은 운영 DNS 연결 전 클라이언트 시연만을 위한 것입니다.

## 구성

외부 사용자는 ngrok HTTPS 주소로 접속합니다. 요청은 ngrok 컨테이너, Nginx, Spring Boot 순서로 전달되며 PostgreSQL과 ClamAV는 외부 포트를 열지 않습니다.

## 최초 1회 준비

1. Docker Desktop을 실행합니다.
2. ngrok 계정을 만들고 대시보드에서 Authtoken과 제공된 고정 도메인을 확인합니다.
3. 프로젝트 폴더에서 다음 명령을 실행합니다.

```powershell
Copy-Item .env.demo.example .env.demo
notepad .env.demo
```

`.env.demo`에서 아래 값은 반드시 변경합니다.

- `NGROK_AUTHTOKEN`
- `NGROK_DOMAIN` — `https://`를 제외한 호스트만 입력
- `DB_PASSWORD`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD` — 12자 이상

`.env.demo`는 Git에 포함되지 않습니다.

## 실행

```powershell
Set-Location C:\Kumsung_ENC_Smart_Platform
.\scripts\demo-up.ps1
```

- 고객 시연 주소: `https://설정한주소.ngrok-free.app`
- 로컬 이메일함: `http://localhost:8025`
- ngrok 요청 검사: `http://localhost:4040`
- 로컬 우회 점검: `http://localhost:8088`

ClamAV 시그니처 초기화 때문에 최초 실행은 수 분 걸릴 수 있습니다.

## 이메일 인증 시연

기본 설정은 실제 이메일을 발송하지 않고 Mailpit에 안전하게 수집합니다.

1. 공개 주소에서 고객 계정을 가입합니다.
2. 발표자 PC에서 `http://localhost:8025`를 엽니다.
3. 인증 메일의 ngrok 링크를 열고 로그인합니다.

시연 환경은 Mailpit을 사용합니다. 회사 도메인 메일 계정이 발급된 뒤 운영 환경에서만 `scripts/configure-smtp.ps1 -Environment production -Provider company`로 연결하며, 비밀번호는 `.secrets/mail_password`에 분리합니다.

## 상태와 로그

```powershell
docker compose --env-file .env.demo -f compose.demo.yml ps
docker compose --env-file .env.demo -f compose.demo.yml logs -f app nginx ngrok
```

## 종료

```powershell
.\scripts\demo-down.ps1
```

종료해도 DB와 업로드 파일은 Docker 볼륨에 유지됩니다. 시연이 끝나면 터널을 반드시 종료하고 ngrok 주소를 더 이상 공유하지 마세요.

데이터까지 완전히 삭제해야 할 때만 다음 명령을 사용합니다.

```powershell
docker compose --env-file .env.demo -f compose.demo.yml down -v
```

`-v`는 시연 DB와 업로드 볼륨을 복구 없이 삭제합니다.
