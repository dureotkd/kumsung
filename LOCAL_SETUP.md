# 로컬 실행 가이드

이 문서는 Windows PowerShell에서 Kumsung ENC Smart Platform을 로컬로 실행하는 방법을 설명합니다.

## 프로젝트 구성

- 백엔드/웹: Java 21, Spring Boot 3.5.14, 정적 HTML·CSS·JavaScript
- 데이터베이스: PostgreSQL 17, Flyway 마이그레이션 17개
- 로컬 메일: Mailpit SMTP 및 웹 수신함
- 파일 저장: 로컬 디스크(`.local/data/uploads`)
- 선택 기능: Google Sheets 연동, ClamAV, S3 저장소는 로컬 기본 실행에서 비활성화

애플리케이션이 시작되면 Flyway가 빈 데이터베이스에 테이블과 초기 SMART SHOP 상품을 자동 생성합니다.

## 콘텐츠 운영 방법

통합 관리자 화면(`http://localhost:8080/admin.html`)과 SHOP 관리자 화면(`http://localhost:8080/shop-admin.html`)에서 담당 영역별 콘텐츠를 관리합니다.

### SHOP 제품 관리

1. SHOP 관리자 화면에 로그인하고 왼쪽 메뉴에서 **제품 관리**를 선택합니다.
2. 제품명, 제품 코드, 분류, 판매 가격, 설명, 노출 순서와 대표 이미지를 입력합니다.
3. **SMART SHOP에 공개**를 선택하고 저장하면 고객용 `shop.html`에 가격과 제품 정보가 반영됩니다. 가격을 비우면 고객 화면에는 `가격 문의`로 표시됩니다.
4. 초기 데이터베이스에는 제품 25개가 자동 등록됩니다. 목록의 **수정**으로 제품 정보와 이미지를 교체할 수 있고, **공개/비공개** 버튼으로 고객 노출을 제어합니다.

가격이 1원 이상이고 공개 상태인 제품에는 고객용 `shop.html`에서 **구매하기** 버튼이 표시됩니다. 고객이 수량과 구매자·배송 정보를 입력하면 서버가 관리자 등록 단가를 다시 조회해 주문 금액을 계산하므로 브라우저에서 금액을 바꿔도 승인되지 않습니다.

대표 이미지는 JPG, PNG, WEBP 형식이며 파일당 15MB 이하로 업로드합니다. 기존 제품 문의 접수와 관리자 **SHOP 문의** 처리는 그대로 유지됩니다.

### SHOP 관리자와 토스페이먼츠 설정

1. 최고관리자가 통합 관리자 화면의 **관리자 관리**에서 권한을 `SHOP 관리자`로 선택해 담당자를 초대합니다.
2. 담당자는 메일 링크에서 자신의 12자 이상 비밀번호를 설정합니다.
3. SHOP 관리자 계정은 로그인 후 전용 2차 비밀번호 확인 화면으로 이동합니다.
4. 로그인 비밀번호와 별개인 `SHOP_ADMIN_ACCESS_PASSWORD`를 입력한 관리자만 `shop-admin.html`과 SHOP 관리 API를 사용할 수 있습니다. 확인 화면에는 아이디 입력란이 없으며, 주소를 직접 입력하면 매번 2차 비밀번호 확인 화면으로 이동합니다.
5. 토스페이먼츠 메뉴에서 테스트·라이브 모드와 클라이언트 키를 저장합니다. 서버 설정으로 관리하려면 `.env.local`의 `TOSS_CLIENT_KEY`를 사용합니다.
6. 시크릿 키는 화면이나 DB에 입력하지 않고 `.env.local`의 `TOSS_SECRET_KEY`에 설정한 뒤 애플리케이션을 재시작합니다.

클라이언트 키와 시크릿 키는 같은 상점(MID)에서 발급된 같은 모드·같은 연동 유형의 키여야 합니다. 현재 `payment()` 결제창에는 **API 개별 연동 키**의 `ck/sk` 세트를 사용합니다. 고객 결제 인증 뒤 서버가 주문번호와 DB 금액을 검증하고 `/v1/payments/confirm`으로 승인합니다. 결제 실패·사용자 취소도 주문 상태에 기록되며, 결제 완료 시 운영 담당자 알림과 영수증 저장이 처리됩니다. SHOP 관리자는 주문 내역에서 결제를 전액 취소할 수 있습니다.

### 기술혁신 자료

1. 왼쪽 메뉴에서 **기술혁신 자료**를 선택합니다.
2. 연구전담부서·특허/인증·기술자료·Knowledge Library·스마트공장 중 분류를 선택하고 제목, 설명, 노출 순서, 대표 이미지, 고객 다운로드 파일, 다운로드 비밀번호를 입력합니다.
3. 업로드하면 대표 이미지는 `projects.html`에 카드로 공개됩니다.
4. 고객은 카드의 다운로드 버튼을 누르고 올바른 비밀번호를 입력해야 원본 파일을 받을 수 있습니다.

비밀번호는 서버에 평문으로 저장되지 않고 BCrypt 해시로 저장됩니다. 이미지 파일은 15MB 이하, 다운로드 파일은 PDF·이미지·DWG·DXF·Excel·Word·ZIP 형식으로 50MB 이하를 권장합니다. 비밀번호를 잊은 경우 기존 값을 확인할 수 없으므로 자료를 삭제하고 새 비밀번호로 다시 업로드합니다.

### 회사소식·시공사례

1. 왼쪽 메뉴에서 **회사소식·시공사례**를 선택합니다.
2. 구분을 회사소식 또는 시공사례로 선택하고 제목, 내용, 게시 이미지를 입력합니다.
3. **고객센터에 공개**와 필요 시 **상단 고정**을 선택한 뒤 업로드합니다.
4. 고객용 `support.html`에서 두 탭으로 나뉘어 표시되며, 이미지를 포함한 상세 내용을 확인할 수 있습니다.

기존 고객문의 접수 양식은 콘텐츠 목록 아래에 계속 제공됩니다.

## 가장 빠른 실행 방법

프로젝트 폴더에서 PowerShell을 열고 다음 명령을 실행합니다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\local-up.ps1
```

최초 실행은 포터블 Java 21, Maven, PostgreSQL, Mailpit을 사용자 로컬 폴더에 내려받고 Maven 의존성을 받기 때문에 몇 분 정도 걸릴 수 있습니다. 관리자 권한이나 Docker Desktop은 필요하지 않습니다. 두 번째 실행부터는 설치된 런타임과 빌드 결과를 재사용합니다.

실행 완료 후 주소는 다음과 같습니다.

| 용도 | 주소 |
|---|---|
| 메인 웹 | `http://localhost:8080` |
| 고객 포털 | `http://localhost:8080/portal.html` |
| 관리자 화면 | `http://localhost:8080/admin.html` |
| SHOP 관리자 화면 | `http://localhost:8080/shop-admin.html` |
| 로컬 이메일 수신함 | `http://localhost:8025` |
| 헬스 체크 | `http://localhost:8080/actuator/health` |

기본 로컬 관리자 계정:

- 이메일: `admin@localhost.test`
- 비밀번호: `local-admin-1234`
- SHOP 2차 비밀번호: `local-shop-admin-5678` (`.env.local`의 `SHOP_ADMIN_ACCESS_PASSWORD`로 변경)

이 계정과 비밀번호는 로컬 개발 전용입니다. 운영 환경에서는 사용하지 마세요.

## 종료와 재실행

종료:

```powershell
.\scripts\local-down.ps1
```

재실행:

```powershell
.\scripts\local-up.ps1
```

종료해도 DB, 업로드 파일, Mailpit 수신 메일은 프로젝트의 `.local` 폴더에 보존됩니다.

## 로컬 설정 변경

기본값을 변경하려면 예제 파일을 복사합니다.

```powershell
Copy-Item .env.local.example .env.local
notepad .env.local
```

변경할 수 있는 주요 값:

- `SERVER_PORT`: 웹 포트, 기본 `8080`
- `DB_PORT`: PostgreSQL 포트, 기본 `5432`
- `MAILPIT_SMTP_PORT`: 로컬 SMTP 포트, 기본 `1025`
- `MAILPIT_UI_PORT`: Mailpit 웹 포트, 기본 `8025`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`: 최초 로컬 관리자 계정
- `TOSS_CLIENT_KEY`: 토스페이먼츠 브라우저 SDK용 테스트 또는 라이브 클라이언트 키
- `TOSS_SECRET_KEY`: 위 클라이언트 키와 같은 세트인 서버 전용 시크릿 키
- `SHOP_ADMIN_ACCESS_PASSWORD`: SHOP 관리자 진입 전용 2차 비밀번호

포트를 바꾸기 전에는 `local-down.ps1`로 서비스를 종료하세요. `SERVER_PORT`를 변경하면 `APP_BASE_URL`도 같은 포트로 맞춰야 합니다.

이미 생성된 관리자의 이메일·비밀번호는 환경변수를 바꾼다고 자동 변경되지 않습니다. 완전히 새로운 로컬 DB로 시작할 때만 새 값이 반영됩니다.

`DB_PASSWORD`도 PostgreSQL 데이터가 처음 만들어질 때 지정됩니다. 기존 `.local/data/postgres`를 유지한 채 이 값만 바꾸면 접속할 수 없으므로 초기화 전이 아니라면 변경하지 않는 편이 안전합니다.

## 테스트와 수동 빌드

포터블 런타임만 먼저 준비하려면:

```powershell
.\scripts\local-bootstrap.ps1
```

현재 PowerShell 세션에서 포터블 Java와 Maven으로 테스트하려면:

```powershell
$runtime = Join-Path $env:LOCALAPPDATA 'KumsungEncSmartPlatform\runtime'
$env:JAVA_HOME = Join-Path $runtime 'jdk-21.0.12+8'
& (Join-Path $runtime 'apache-maven-3.9.11\bin\mvn.cmd') test
```

실제 PostgreSQL을 사용하는 통합 테스트까지 실행하려면 운영용 로컬 DB와 분리된 테스트 DB를 먼저 만든 뒤 실행합니다.

```powershell
$runtime = Join-Path $env:LOCALAPPDATA 'KumsungEncSmartPlatform\runtime'
$env:PGPASSWORD = 'kumsung_dev_password'
& (Join-Path $runtime 'postgresql-17.11\pgsql\bin\createdb.exe') `
  -h 127.0.0.1 -p 5432 -U kumsung kumsung_enc_test

$env:JAVA_HOME = Join-Path $runtime 'jdk-21.0.12+8'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/kumsung_enc_test'
$env:SPRING_DATASOURCE_USERNAME = 'kumsung'
$env:SPRING_DATASOURCE_PASSWORD = 'kumsung_dev_password'
& (Join-Path $runtime 'apache-maven-3.9.11\bin\mvn.cmd') '-Dintegration=true' test
```

테스트 DB가 이미 존재하면 `createdb` 단계는 생략합니다.

애플리케이션 로그는 아래에 저장됩니다.

```text
.local/logs/app.out.log
.local/logs/app.err.log
.local/logs/postgres.log
.local/logs/mailpit.out.log
.local/logs/mailpit.err.log
```

## Docker가 설치된 PC에서 실행

기존 프로젝트 구성은 Docker 방식도 지원합니다.

```powershell
docker compose up -d
$env:ADMIN_EMAIL='admin@example.com'
$env:ADMIN_PASSWORD='replace-with-a-strong-password'
mvn spring-boot:run
```

이 방식은 Docker Desktop, Java 21, Maven 3.9 이상이 모두 PATH에 설치되어 있어야 합니다.

## 자주 발생하는 문제

### PowerShell 스크립트 실행이 차단됨

현재 터미널에서만 실행 정책을 완화합니다.

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

### 8080, 5432, 1025, 8025 포트를 이미 사용 중

사용 중인 프로그램을 종료하거나 `.env.local`에서 해당 포트를 변경합니다.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080,5432,1025,8025
```

### 실행 실패 원인 확인

```powershell
Get-Content .local\logs\app.out.log -Tail 100
Get-Content .local\logs\app.err.log -Tail 100
Get-Content .local\logs\postgres.log -Tail 100
```

### 로컬 데이터를 완전히 초기화해야 함

먼저 서비스를 종료한 뒤 `.local` 폴더를 별도 위치에 백업하거나 삭제합니다. 이 작업은 로컬 DB, 업로드 파일, 수신 메일을 모두 없애므로 필요한 데이터가 없는지 먼저 확인하세요.

```powershell
.\scripts\local-down.ps1
Move-Item .local .local-backup
.\scripts\local-up.ps1
```
