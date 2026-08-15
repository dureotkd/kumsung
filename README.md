# Kumsung ENC Smart Platform

홈페이지 도메인 기반 회사 메일 SMTP 연결 방법은 [SMTP_SETUP.md](SMTP_SETUP.md)를 참고하세요. 데모는 Mailpit을 사용하며 개인 Gmail·한메일 계정은 운영 발신 계정으로 사용하지 않습니다.

다중 관리자 초대 및 권한 운영 방법은 [ADMIN_MANAGEMENT.md](ADMIN_MANAGEMENT.md)를 참고하세요.

견적·SMART SHOP의 선택적 Google Sheets 연동 방법은 [GOOGLE_SHEETS_SETUP.md](GOOGLE_SHEETS_SETUP.md)를 참고하세요. 고객문의는 PostgreSQL과 관리자 페이지에서 직접 확인·답변하며 Sheets로 복제하지 않습니다.

(주)금성이엔씨 온라인 견적접수 및 고객 업무 포털입니다.

## 핵심 기능

- 이메일 확인 화면에서 사용자가 직접 승인하는 CSRF 보호 POST 인증과 고객 ID 기반 견적 소유권·접근 통제
- 견적의뢰정보를 하나의 메모형 UI로 입력하고 각 값을 검증하여 기존 DB 컬럼에 저장
- 결제 없는 SMART SHOP 제품 선택·수량·사양 문의, 접수번호, 관리자·고객 포털 상태 관리
- 관리자에서 관리하는 SHOP 제품 25개와 제품별 대표 이미지·공개 상태
- 대표 이미지 기반 기술혁신 자료와 BCrypt 비밀번호 보호 파일 다운로드
- 관리자 이미지 업로드 방식의 회사소식·시공사례 고객센터
- 비회원 고객문의 접수와 견적·쇼핑·고객문의 Google Sheets Outbox 연동
- PDF/JPG/PNG/DWG/DXF/Excel/ZIP 다중 첨부 및 파일 시그니처 검증
- ClamAV 악성코드 검사와 원자적 파일 저장
- PostgreSQL 및 Flyway 마이그레이션
- 배치 소유권 주장 방식의 이메일 Outbox, 최대 5회 지수 백오프 재시도
- 개인정보 동의 이력, 데이터 내보내기, 계정 삭제·텍스트 익명화·고객 첨부파일 파기, 보존기간 정리
- 견적 상태·이력·상시 추가자료·고객/담당자 웹하드 공유
- 견적서 PDF·Excel 자동 생성, 문서 다운로드·전자승인
- 계약서 확인·수락·거절과 변경 불가능한 감사 이력
- 고객 ID 기반 프로젝트·계약·납품·세금계산서·A/S·고객센터 소유권
- 문서·메시지·견적 완료·업무 상태·A/S·고객센터 Outbox 알림
- 운영 Docker, Caddy HTTPS, 헬스체크, PostgreSQL·파일 백업

## 로컬 개발

요구사항: Java 21, Maven 3.9+, Docker

Docker와 Java가 설치되지 않은 Windows PC에서는 포터블 런타임을 자동 구성하는 [LOCAL_SETUP.md](LOCAL_SETUP.md)를 따라 실행할 수 있습니다.

```powershell
docker compose up -d
mvn spring-boot:run
```

- 웹: `http://localhost:8080`
- 개발용 이메일: `http://localhost:8025`
- 고객 포털: `http://localhost:8080/portal.html`
- 관리자: `http://localhost:8080/admin.html`

기본 관리자 계정은 존재하지 않습니다. 개발 중 관리자가 필요하면 실행 전에 강한 임의 비밀번호로 환경변수를 지정하세요.

```powershell
$env:ADMIN_EMAIL='admin@example.com'
$env:ADMIN_PASSWORD='replace-with-a-strong-password'
mvn spring-boot:run
```

## 운영 배포

1. `.env.production.example`을 `.env.production`으로 복사합니다.
2. 실제 도메인, SMTP, 견적 담당 이메일과 강한 DB 비밀번호를 입력합니다. 최초 배포에만 관리자 이메일·비밀번호도 입력합니다.
3. DNS가 운영 서버를 가리키는지 확인합니다.
4. 다음 명령으로 배포합니다.

```bash
docker compose --env-file .env.production -f compose.prod.yml up -d --build
docker compose --env-file .env.production -f compose.prod.yml ps
```

Caddy가 도메인의 HTTPS 인증서를 자동 발급하며 PostgreSQL과 ClamAV는 외부 포트로 공개되지 않습니다.

최초 최고관리자 로그인을 확인한 뒤 `.env.production`에서 `ADMIN_EMAIL`, `ADMIN_PASSWORD` 값을 비우고 다시 배포하세요. 기존 관리자 계정은 DB에 유지되며 이후 관리자는 관리자 화면의 초대 기능으로 추가합니다.

## 백업과 복구

운영 Compose의 `backup` 서비스가 매일 PostgreSQL dump와 업로드 파일 압축본을 `backups_data` 볼륨에 저장합니다. 기본 보존기간은 30일입니다.

- 백업 스크립트: `scripts/backup.sh`
- 복구 스크립트: `scripts/restore.sh`

동일 서버 장애에 대비해 `backups_data`를 별도 저장소로 주기적으로 복제해야 합니다.

## 테스트

```powershell
mvn test
```

실제 PostgreSQL 통합 테스트는 `integration=true` 시스템 속성과 테스트 DB 접속 환경변수를 사용합니다.

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:55433/kumsung_enc'
$env:SPRING_DATASOURCE_USERNAME='kumsung'
$env:SPRING_DATASOURCE_PASSWORD='password'
mvn '-Dintegration=true' test
```

## 운영 확인

- 개인정보 처리방침의 담당 이메일과 시행 버전 확인
- SMTP SPF/DKIM/DMARC 설정
- 백업 복구훈련 및 외부 백업 복제
- 관리자 비밀번호 비밀관리 도구 저장
- 부하 테스트 및 보안 점검
