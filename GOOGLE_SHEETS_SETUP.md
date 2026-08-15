# Google Sheets 고객문의 연동

온라인 견적의뢰와 SMART SHOP 제품 문의는 먼저 PostgreSQL과 `sheet_outbox`에 저장됩니다. Google Sheets가 일시적으로 실패해도 고객 접수는 유지되며 최대 8회 지수 백오프로 재시도합니다. 공개·회원 고객문의는 개인정보 중복 복제를 피하고 즉시 답변할 수 있도록 PostgreSQL과 관리자 페이지에서만 관리합니다.

각 전송에는 고유 Outbox 이벤트 ID가 포함됩니다. Apps Script는 첫 번째 열의 이벤트 ID를 확인해 HTTP 응답 유실 후 재시도되는 경우에도 같은 문의 행을 중복 추가하지 않습니다.

## 1. Google Sheet 준비

1. 문의를 모을 Google Sheet를 새로 만듭니다.
2. 주소에서 `/d/`와 `/edit` 사이의 Spreadsheet ID를 복사합니다.
3. Sheet 탭은 미리 만들 필요가 없습니다. `온라인 견적의뢰`, `SMART SHOP 문의` 탭이 자동 생성됩니다.

## 2. Apps Script 배포

1. Google Sheet에서 `확장 프로그램 → Apps Script`를 엽니다.
2. [scripts/google-sheets-webhook.gs](scripts/google-sheets-webhook.gs)의 내용을 붙여넣습니다.
3. `프로젝트 설정 → 스크립트 속성`에 다음 값을 추가합니다.
   - `SPREADSHEET_ID`: 1단계에서 복사한 값
   - `WEBHOOK_SECRET`: 32자 이상의 임의 문자열
4. `배포 → 새 배포 → 웹 앱`을 선택합니다.
   - 실행 사용자: 나
   - 액세스 권한: 모든 사용자
5. 배포 후 발급된 `/exec` URL을 복사합니다.

웹 앱 URL은 외부에서 호출 가능하지만, 본문에 포함된 `WEBHOOK_SECRET`이 일치해야만 Sheet에 기록됩니다. 비밀값은 소스코드에 직접 작성하지 않습니다.

## 3. 서버 환경변수

```env
GOOGLE_SHEETS_ENABLED=true
GOOGLE_SHEETS_WEBHOOK_URL=https://script.google.com/macros/s/배포ID/exec
GOOGLE_SHEETS_WEBHOOK_SECRET=스크립트_속성과_동일한_긴_임의값
```

설정 후 애플리케이션 컨테이너를 재배포합니다.

```powershell
docker compose --env-file .env.demo -p kumsung-enc-demo -f compose.demo.yml up -d --build app nginx ngrok
```

운영 환경은 `.env.production`에 같은 값을 설정하고 `compose.prod.yml`로 재배포합니다.

## 4. 확인 및 장애 처리

접수 후 다음 SQL로 전송 상태를 확인할 수 있습니다.

```sql
select id,event_type,reference_type,reference_id,status,attempts,last_error,created_at,sent_at
from sheet_outbox
order by id desc;
```

- `PENDING`: 연동 비활성 또는 전송 대기
- `PROCESSING`: 작업자가 전송 중
- `RETRY`: 일시 실패 후 재시도 대기
- `SENT`: Sheet 기록 완료
- `FAILED`: 8회 실패. URL·권한·비밀값을 확인한 뒤 운영자가 재처리해야 함

관리자 화면의 **메일 설정 → Google Sheets 연동 내역**에서도 실패 원인을 확인하고 재처리할 수 있습니다. Google Sheet에 복제된 개인정보의 보존기간과 삭제는 Sheet 또는 Workspace 보존정책에서 별도로 관리해야 합니다.

Apps Script를 새로 배포하면 URL이 달라질 수 있으므로 `GOOGLE_SHEETS_WEBHOOK_URL`도 함께 갱신합니다.
