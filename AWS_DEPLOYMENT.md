# (주)금성이엔씨 AWS 최초 배포 가이드

이 문서는 `EC2 1대 + RDS PostgreSQL Single-AZ + 비공개 S3 + CloudFront` 비용 절감형 1차 구성을 기준으로 한다. 애플리케이션은 RDS에 업무 데이터를 저장하고, 고객 도면·견적 문서는 S3 `private/` 경로에 저장한다. S3 객체는 공개하지 않으며 로그인·소유권 또는 관리자 권한을 확인한 애플리케이션만 내려준다.

## 0. 권장 사양

| 리소스 | 최초 사양 | 핵심 설정 |
|---|---|---|
| EC2 | `t3.medium`, Ubuntu 24.04 LTS, gp3 30GB | Docker 실행, Elastic IP, 서울 리전 |
| RDS | PostgreSQL 17, `db.t4g.small`, gp3 30GB | Single-AZ, 비공개 접근, 자동 백업 7일 |
| S3 | 범용 버킷 1개 | 모든 퍼블릭 액세스 차단, 버전 관리 중지, SSE-S3 |
| CloudFront | 배포 1개 | 동적 요청 캐시 금지, CSS/JS/이미지만 캐시 |

## 1. AWS 계정과 비용 안전장치

1. AWS 계정은 반드시 `(주)금성이엔씨` 명의로 만든다.
2. 루트 계정 MFA를 켠다.
3. 배포 담당자용 임시 관리 계정을 만들고, 인계 완료 후 권한 또는 계정을 회수한다.
4. Billing의 AWS Budget에서 월 예산 알림(예: 80%, 100%)을 만든다.
5. 모든 리소스의 리전이 `아시아 태평양(서울) ap-northeast-2`인지 확인한다. CloudFront 인증서만 예외로 미국 동부(버지니아 북부) `us-east-1`에 만든다.

## 2. 네트워크와 보안 그룹

기본 VPC를 사용해도 1차 배포는 가능하다. 다음 보안 그룹 두 개를 만든다.

### `kumsung-web-sg`

- 인바운드 SSH 22: 배포 담당자의 현재 공인 IP `/32`만
- 인바운드 HTTP 80: 최초 인증서 발급 중에는 전체 허용
- 인바운드 HTTPS 443: 최초 확인 중에는 전체 허용
- 운영 안정화 뒤에는 443을 CloudFront 원본 연결용 AWS 관리형 Prefix List로 제한하는 것을 권장
- 아웃바운드: 기본 전체 허용

### `kumsung-rds-sg`

- 인바운드 PostgreSQL 5432: 소스를 IP가 아니라 `kumsung-web-sg`로 지정
- 그 외 인바운드 없음

RDS를 인터넷 공개로 만들지 않는다. 로컬 PC의 DB 도구로 직접 접속해야 할 경우에도 공개 전환 대신 EC2 SSH 터널을 사용한다.

## 3. RDS PostgreSQL 생성

1. RDS → 데이터베이스 생성 → 표준 생성 → PostgreSQL 17을 선택한다.
2. 템플릿은 개발/테스트 또는 프리 티어가 표시되면 비용 조건에 맞게 선택한다.
3. DB 인스턴스 식별자: `kumsung-production-db`
4. 마스터 사용자명은 `postgres` 같은 일반 이름 대신 별도 이름을 사용한다.
5. 인스턴스: `db.t4g.small`, Single-AZ
6. 저장소: gp3 30GB, 자동 확장 최대 100GB
7. 퍼블릭 액세스: `아니요`
8. 보안 그룹: `kumsung-rds-sg`
9. 초기 데이터베이스 이름: `kumsung_enc`
10. 자동 백업 7일, 삭제 방지 활성화, 암호화 활성화

생성 후 엔드포인트를 복사한다. Flyway가 첫 애플리케이션 실행 때 스키마를 자동 생성하므로 빈 RDS에는 별도 SQL 덤프가 필요 없다. 기존 운영 데이터를 옮기는 경우에는 Flyway와 별개로 `pg_dump/pg_restore`가 필요하다.

`.env.aws`의 URL 예시:

```dotenv
DB_URL=jdbc:postgresql://RDS-ENDPOINT:5432/kumsung_enc?sslmode=verify-full&sslrootcert=/app/certs/rds-global-bundle.pem
DB_USERNAME=kumsung_app
```

## 4. S3 버킷과 EC2 IAM Role

1. S3 버킷을 서울 리전에 만든다. 이름은 전 세계에서 고유해야 한다. 예: `kumsungenc-production-files-계정번호`
2. `모든 퍼블릭 액세스 차단`을 그대로 유지한다.
3. 개인정보 삭제 요청 시 객체가 영구 삭제되도록 버킷 버전 관리는 중지 상태로 유지한다.
4. 기본 암호화는 SSE-S3를 켠다.
5. 파일 복구는 별도로 보유한 Synology NAS Pull 백업으로 처리한다.
6. IAM 정책을 `infra/aws/ec2-s3-policy.json`에서 만들되 버킷 이름을 실제 값으로 바꾼다.
7. 신뢰 주체가 EC2인 IAM Role `kumsung-ec2-role`을 만들고 위 정책을 붙인다.
8. 액세스 키를 발급하지 않는다. 애플리케이션은 EC2 Role의 임시 자격증명을 자동 사용한다.

S3에는 고객 파일만 저장된다. DB 백업은 RDS 자동 백업/스냅샷으로 별도 관리한다.

## 5. EC2 생성 및 준비

1. Ubuntu Server 24.04 LTS, `t3.medium`, gp3 30GB로 만든다.
2. 보안 그룹은 `kumsung-web-sg`, IAM Role은 `kumsung-ec2-role`을 연결한다.
3. Elastic IP를 생성해 EC2에 연결한다.
4. SSH 접속 후 Docker Engine과 Docker Compose plugin, Git을 설치한다.
5. 소스 전체를 `/opt/kumsung-platform`에 복제한다.

```bash
cd /opt/kumsung-platform
cp .env.aws.example .env.aws
mkdir -p .secrets
chmod 700 .secrets
printf '%s' 'RDS-DB-PASSWORD' > .secrets/db_password
printf '%s' 'GOOGLE-APP-PASSWORD' > .secrets/mail_password
printf '%s' 'INITIAL-ADMIN-PASSWORD' > .secrets/admin_password
chmod 600 .env.aws .secrets/db_password .secrets/mail_password .secrets/admin_password
```

비밀번호 파일은 Git에 커밋하지 않는다. `.env.aws`의 RDS 엔드포인트, 버킷명, 도메인과 SMTP 계정을 실제 값으로 바꾼다.

실행:

```bash
docker compose --env-file .env.aws -f compose.aws.yml config
docker compose --env-file .env.aws -f compose.aws.yml up -d --build
docker compose --env-file .env.aws -f compose.aws.yml ps
docker compose --env-file .env.aws -f compose.aws.yml logs --tail=200 app
```

최초 관리자 생성 때만 `.env.aws`의 `ADMIN_EMAIL`과 `.secrets/admin_password`를 채운다. 첫 로그인과 운영 관리자 추가 후 `ADMIN_EMAIL`을 비우고 `: > .secrets/admin_password`로 초기 비밀번호를 제거한 다음 컨테이너를 다시 만든다.

## 6. 도메인, 인증서, CloudFront

### 원본 도메인

- `origin.kumsungenc.co.kr` A 레코드를 EC2 Elastic IP에 연결한다.
- Caddy가 이 도메인의 HTTPS 인증서를 자동 발급한다.
- `.env.aws`의 `ORIGIN_DOMAIN=origin.kumsungenc.co.kr`로 둔다.

### CloudFront 인증서

1. ACM 리전을 `미국 동부(버지니아 북부) us-east-1`로 바꾼다.
2. `kumsungenc.co.kr`과 `www.kumsungenc.co.kr` 인증서를 요청한다.
3. DNS 검증 레코드를 등록한다.

### CloudFront 배포

- 원본: `origin.kumsungenc.co.kr`, 원본 프로토콜 HTTPS only
- 기본 동작: Managed-CachingDisabled, 모든 쿠키와 쿼리 문자열을 원본에 전달
- `/api/*`: Managed-CachingDisabled, 모든 쿠키·쿼리·필요 헤더 전달
- `/css/*`, `/js/*`, `/images/*`: Managed-CachingOptimized
- Viewer protocol policy: Redirect HTTP to HTTPS
- 대체 도메인: `kumsungenc.co.kr`, `www.kumsungenc.co.kr`
- 인증서: 위 `us-east-1` ACM 인증서

세션 로그인 때문에 `/api/*`, HTML, 로그인·관리자·포털 페이지를 캐시하면 안 된다.

도메인 최상위(`kumsungenc.co.kr`)를 CloudFront에 바로 연결하려면 Route 53 Alias가 가장 단순하다. Route 53으로 이전한다면 기존 Google Workspace의 MX, SPF, DKIM, 도메인 인증 레코드를 모두 먼저 복제한 후 가비아에서 네임서버를 변경한다.

## 7. 배포 후 필수 점검

1. `/actuator/health`가 `UP`인지 확인
2. 회원가입 → 실제 인증메일 → 인증 → 로그인
3. PDF/JPG/PNG/DWG 첨부 견적 접수
4. S3 버킷 `private/` 아래에 파일 생성 확인
5. 다른 고객 계정으로 해당 파일 다운로드가 차단되는지 확인
6. 관리자 견적 확인·문서 등록·고객 다운로드
7. SMART SHOP 제품별 첨부 접수·관리자 다운로드
8. 고객센터 접수·관리자 답변·실제 이메일 수신
9. 계정 탈퇴 후 관련 S3 객체 삭제 확인
10. EC2 재부팅 후 Docker 서비스와 데이터가 정상인지 확인

## 8. NAS 수동 Pull 백업

Synology NAS에 AWS CLI를 설치하고 읽기 전용 IAM 사용자를 별도로 만든다. EC2 Role 자격증명을 NAS로 복사하지 않는다. NAS용 정책은 `s3:ListBucket`, `s3:GetObject`만 허용한다.

```bash
export S3_BUCKET='실제-버킷명'
export NAS_BACKUP_DIR='/volume1/backup/kumsung-s3'
export AWS_REGION='ap-northeast-2'
sh scripts/nas-s3-pull.sh
```

동기화는 삭제 옵션 없이 실행되므로 S3에서 삭제된 파일도 NAS에 남는다. 월 1회 복원 표본 검사를 수행한다. RDS 데이터는 S3 파일 Pull과 별개이므로 자동 백업과 수동 스냅샷을 함께 점검한다.

## 9. 인계 후 확장 기준

현재 구성은 Single-AZ, 단일 EC2 기준이다. CloudWatch에서 CPU·메모리·디스크·RDS 연결 수와 지연을 모니터링하고, CPU/메모리 사용률이 업무 시간대에 지속적으로 70~80% 이상이면 EC2 이중화와 ALB 연결을 검토한다. 장애 허용이 중요해지면 RDS Multi-AZ로 전환한다.

*첫 1회 배포 이후 실제 사용량을 확인 후 서버 확장은 (주)금성이엔씨에 맡길 예정(유지보수)*

*비용은 실사용 트래픽에 따라 변동될 수 있음.*
