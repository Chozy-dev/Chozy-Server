# Chozy Server

위탁 셀러의 상품 소싱 검증 서비스 Chozy의 Spring Boot 백엔드입니다. 프론트엔드 REST API와 사용자 기능을 담당하며, 상품 수집·공급 점수 계산·정기 추천 발행은 별도 Python Collector가 담당합니다.

현재 진척과 다음 작업은 [서버 현황](docs/CONTEXT.md)에서 확인할 수 있습니다. 기본 실행과 테스트에는 PostgreSQL이나 외부 API가 필요하지 않습니다.

## 기술 환경

| 항목 | 버전/구성 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.16 |
| 빌드 | Maven Wrapper 3.9.11 |
| 웹·보안 | Spring MVC, Spring Security, Bean Validation |
| 저장 기반 | Spring Data JPA, PostgreSQL 드라이버 |
| API 문서 | springdoc OpenAPI 2.8.17 |
| 테스트 | JUnit 5, MockMvc, Spring Security Test |

## 빠른 시작

`Chozy_Server` 디렉터리에서 실행합니다. JDK 21을 설치하고 `JAVA_HOME`을 해당 JDK 경로로 지정해야 합니다. 별도 Maven 설치는 필요 없으며 첫 빌드에는 Maven Central 연결이 필요합니다.

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd -B verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

macOS/Linux에서는 `./mvnw`를 사용합니다. 실행 권한이 없으면 `sh ./mvnw`로 실행할 수 있습니다.

- 서버: `http://localhost:8080`
- 전체 API의 Swagger 테스트는 [프론트엔드 API 테스트](#프론트엔드-api-테스트)를 참고합니다.
- 종료: 실행 터미널에서 `Ctrl+C`


빌드한 JAR를 실행하려면:

```powershell
java -jar target/server-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

## IntelliJ 설정

1. `Chozy_Server/pom.xml`을 Maven 프로젝트로 연결합니다.
2. Project SDK, Maven Importer/Runner JDK를 21로 지정합니다.
3. `ChozyServerApplication`을 실행 대상으로 선택합니다.
4. Active profiles를 `dev`로 설정합니다. 설정 UI가 없으면 VM options에 `-Dspring.profiles.active=dev`를 지정합니다.
5. 필요한 환경변수를 Run Configuration의 Environment variables에 입력합니다.

## 환경 설정

설정 예시는 [.env.example](.env.example)에 있습니다. Spring Boot는 이 파일이나 `.env`를 자동으로 읽지 않습니다. IDE 또는 OS 환경변수로 주입합니다.

```powershell
$env:SERVER_PORT='8080'
$env:CORS_ALLOWED_ORIGINS='http://localhost:3000'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

| 프로필 | DB | API 문서 |
| --- | --- | --- |
| 기본값 `offline` | 비활성 | 비활성 |
| `dev` (`offline` 포함) | 비활성 | 공개 |
| `dbdev` | 격리된 로컬 PostgreSQL, Flyway 적용 | 공개 |
| `database` | 명시적인 접속 설정 필요 | 기본 비활성 |
| `prod` (`database` 포함) | 명시적인 접속 설정 필요 | 기본 비활성, 별도 제어 |

`offline/dev`와 DB 프로필을 함께 지정하지 않습니다. `dbdev`와 `database/prod`도 혼용할 수 없습니다. 운영 DB 연동과 Spring 전용 계정 전환은 미완료입니다. Hibernate DDL·SQL 초기화는 실행하지 않으며 Flyway는 `dbdev`에서만 적용합니다.

### 격리 개발 DB 실행

Collector Compose를 사용하지 않습니다. 아래 명령은 Spring 디렉터리에서만 실행합니다. 먼저 55432 포트와 `chozy-spring-dev` 리소스 이름이 다른 용도로 사용되지 않는지 확인합니다.

```powershell
$env:CHOZY_DEV_DB_PASSWORD='<개발용 비밀번호>'
docker compose -f compose.dev.yml up -d --wait
# 애플리케이션 종료 후 DB만 중지 (volume 보존)
docker compose -f compose.dev.yml stop
# 다시 시작
docker compose -f compose.dev.yml up -d --wait
```

JDBC 주소는 `jdbc:postgresql://127.0.0.1:55432/chozy_spring_dev`이고 사용자는 `chozy_spring_dev`입니다. 비밀번호는 기존 volume 초기화 시 정한 값을 계속 사용합니다. 비밀번호 환경변수 변경만으로 기존 DB 비밀번호가 바뀌지는 않습니다. `.env.dbdev`에 저장한 경우 Compose에는 `--env-file .env.dbdev`를 명시하고 Spring에는 별도 환경변수로 주입합니다. 이 파일은 Git에서 제외됩니다.

DB가 이미 실행 중이면 [Spring 기동](#프론트엔드-api-테스트)으로 넘어갑니다. `dbdev`는 기동 시 이 개발 DB에 대기 중인 Flyway 마이그레이션을 적용합니다. 보존할 개발 데이터가 있으면 기동 전 백업을 확보합니다.

통합 테스트 실행 방법은 [검증](#검증), Flyway 설정은 [BACKEND](docs/BACKEND.md#환경과-migration-경계)를 참고합니다.

## 프로젝트 구조

| 경로 | 내용 |
| --- | --- |
| `src/main/java/com/chozy/server/` | 기능별 API, `common` 공통 웹·보안, `integration/external` 외부 클라이언트 |
| `src/main/resources/` | 공통·프로필별 설정과 `db/migration/chozy` |
| `src/test/java/com/chozy/server/` | 기능별 테스트와 `support` 공용 fixture |
| `docs/` | 현재 구현·개발 규칙·참고 원본 |
| `target/` | 빌드 산출물과 검증 로그 |

패키지별 책임은 [백엔드 개발 규칙](docs/BACKEND.md#패키지와-의존-방향)을 따릅니다.

## 검증

```powershell
.\mvnw.cmd -B verify
.\mvnw.cmd '-Dtest=GlobalExceptionHandlerTest' test
.\mvnw.cmd -B -Pdb-test verify
```

기본 검증은 DB·외부 API 없이 실행하며 `target/surefire-reports/`에 결과를 저장합니다. `db-test`는 Docker에서 별도 PostgreSQL 16을 시작하고 `target/failsafe-reports/`에 결과를 저장합니다. 실행 JAR는 `target/server-0.0.1-SNAPSHOT.jar`입니다.

## 개발 문서

- [서버 현황](docs/CONTEXT.md) — 현재 진척, 구조 요약, 남은 문제와 다음 작업
- [백엔드 구조·개발 규칙](docs/BACKEND.md): 패키지 책임, 응답·보안 계약, DB 관리와 외부 연동 경계, 미확정 사항
- [기획서·API 명세](docs/references/): API 구현 시 확인할 원본 자료
- [Collector 현재 상태](../product-data-collector/docs/CONTEXT.md): 수집·발행 시스템의 현재 구현 및 운영 미완료 항목

작업 결과는 Git에서 제외한 프로젝트 최상위 `temp.md`에 최신 내용으로 덮어씁니다. 변경으로 진척이나 제약이 달라지면 CONTEXT를 갱신합니다.


## 프론트엔드 API 테스트

### 직접 입력 정밀분석 실데이터 검증

#### 활성화 전 확인

1. 검증 전용 Spring 업무 DB에서 Flyway V12 적용을 확인합니다. `dbdev`는 기동 시 개발 DB에 대기 중인 migration을 적용합니다. `database`를 사용하면 대상 DB의 적용 이력을 미리 확인합니다. 시장 분석 워커는 활성화되면 기존 작업도 가져갈 수 있으므로 `chozy.manual_analysis_jobs`에 `PENDING`·`PROCESSING` 작업이 없고 검증 중 신규 접수가 테스트 회원으로 제한되는 환경을 사용합니다.
2. 승인된 공동 호출 한도 DB에 **Spring 전용 로그인**으로 접속합니다. 아래 조회로 대상 DB, 로그인과 두 함수의 접근 권한을 확인합니다. 두 행 모두 함수가 존재하고 `public_schema_usage`·`can_execute`가 `true`여야 합니다. 함수 실행 권한 확인에는 실제 예약이나 차단 호출이 필요하지 않습니다.

   ```sql
   SELECT current_database(), session_user;

   WITH budget_functions(signature) AS (
     VALUES ('public.reserve_api_call(text,text,boolean)'),
            ('public.block_api_account(text,text)')
   )
   SELECT signature,
          to_regprocedure(signature) IS NOT NULL AS function_exists,
          has_schema_privilege(session_user, 'public', 'USAGE') AS public_schema_usage,
          coalesce(has_function_privilege(session_user, to_regprocedure(signature), 'EXECUTE'), false)
            AS can_execute
   FROM budget_functions;
   ```
3. 공동 예산 운영자가 최소 권한 적용 상태와 쿠팡 `default`, 네이버 `ad`·`trend`의 실제 계정 매핑, 공유 연동 상태(`platform_integrated`), 차단 상태, 현재 구간의 남은 예산과 호출 간격을 확인합니다. 캐시가 비어 있으면 한 건에 쿠팡 최대 2회, 네이버 검색광고 1회, 데이터랩 월간·일간 각 1회의 HTTP 호출이 발생할 수 있습니다. 간격 제한이 걸리면 엔진은 1초씩 기다리며 예약을 최대 두 번 더 시도합니다. [시장 데이터와 호출 한도 계약](docs/BACKEND.md#직접-입력-시장-데이터)
4. 위 조건을 확인한 후 [.env.example](.env.example)의 시장 분석 설정과 `APP_ANALYSIS_MARKET_ENABLED=true`를 검증 인스턴스에 주입합니다. Spring Boot는 환경 파일을 자동으로 읽지 않습니다. 기존 로컬 설정이 `NAVER_SEARCHAD_*`·`NAVER_API_HUB_*`를 사용한다면 [.env.example](.env.example)의 `NAVER_AD_*`·`NAVER_HUB_*` 이름으로 주입합니다. 공동 예산 DB와 공급처 연동은 실제 작업에서 확인합니다.

#### 한 건 실행과 판정

1. 테스트 회원의 JWT로 `GET /home/report-usage`를 조회해 `period`, `usedCount`, `remainingCount`를 기록합니다. 같은 집계 기간에 전후를 비교할 수 있고 잔여 사용량이 있는 회원으로 `POST /search/analyses/manual`을 한 번 요청합니다. 아래는 요청 형식 예시입니다. 실제 검증에는 시장 데이터가 나올 것으로 예상하는 상품을 선택합니다.

   ```json
   {
     "productName": "스테인리스 텀블러 500ml",
     "wholesalePrice": 8000,
     "shippingFee": 3000,
     "hasPricePolicy": false,
     "keyword": "텀블러"
   }
   ```
2. HTTP 202와 `data.analysisId`를 확인하고 `GET /search/analyses/{analysisId}/status`를 조회합니다. 대기 0, 작업 시작 1, 키워드 조회 4, 시장 데이터 조회 5는 조회 시점에 따라 건너뛸 수 있습니다. 완료는 `COMPLETED`, `currentStep: 7`, `reportId`로 확인합니다. `FAILED`이면 작업 ID, 마지막 단계, 실패 시각과 공동 정책 상태를 기록하고 원인을 확인한 뒤 새 요청 여부를 결정합니다.
3. `GET /search/reports/{reportId}`에서 작업 ID, 시장 표본과 시장가, 네이버 트렌드, 계산 대기나 결측 안내를 확인합니다. 필요하면 `PATCH /search/reports/{reportId}/inputs`로 `coupangCategoryCode`와 시장가가 없을 때의 `expectedSellingPrice`를 입력합니다. 정상 완료 시 `usedCount`는 1 증가하고 `remainingCount`는 1 감소해야 합니다. 재조회·추가 입력 후에도 그 값이 유지되는지 확인합니다. 데이터랩 일일 예산 소진으로 트렌드·지속성이 제외된 완료 건과 실패 건은 사용량이 유지돼야 합니다. 소진과 공급처 제한 응답은 격리 회귀에서 재현합니다.
4. 공동 예산 운영자와 실행 전후의 계정·API별 예약량을 같은 집계 구간에서 대조합니다. 처음 띄운 검증 인스턴스에서 정상 수행했다면 쿠팡 `default` 1~2회, 네이버 `ad` 1회와 `trend` 2회가 예상됩니다. 다른 작업의 동시 예약이나 구간 경계가 섞이면 시각과 집계 구간을 함께 기록합니다. 검증 기록에는 요청 시각, 작업·리포트 ID, 예약량과 차감량을 남깁니다.

### Swagger에서 개발 API 테스트

Swagger에서 인증·회원·홈·검색·리포트·관심상품·알림 API를 테스트하려면 기존 [격리 개발 DB](#격리-개발-db-실행)를 실행하고 `dbdev`로 Spring을 기동합니다. Swagger를 활성화하는 추가 플래그는 필요 없습니다. DB 없이 실행하는 `dev`에서는 공개 쇼핑인사이트 분야, 쿠팡 대분류·기본 수수료율(`GET /search/coupang-categories`), 지원하지 않는 상품 URL의 직접 입력 안내를 확인할 수 있습니다. 상품·옵션 조회 제공자 또는 정밀분석 엔진이 필요한 요청은 연결 전까지 R003/503을 반환합니다. 리포트 상세·의견·관심상품 등록에는 본인의 저장된 리포트가 필요합니다.

`GET /search/categories`는 로그인 없이 호출할 수 있습니다. `POST /search/screenings`에 Bearer 토큰을 붙이면 성공 결과가 회원 최근 기록에 저장됩니다. 스크리닝과 쇼핑 반응 계산에 필요한 네이버 데이터 연결 전에는 S001/503을 반환합니다.

`GET /home/products/recommended?source=DOMEME`는 로그인 없이 호출할 수 있습니다. 소싱처는 `DOMEME`, `OWNERCLAN` 중 하나이며 `size`를 생략하면 10개까지 요청합니다. `dbdev` 또는 `database`에서 [.env.example](.env.example)의 추천 조회 설정과 `APP_HOME_RECOMMENDATIONS_ENABLED=true`를 주입하면 Collector의 최신 완료 발행본을 읽습니다. Collector DB 로그인에는 `public.read_supply_home(text, integer)` 실행 권한이 필요합니다. 설정 전에는 H001/503을 반환합니다.

홈 시장 조회에는 `APP_HOME_MARKET_PUBLICATIONS_ENABLED=true`와 [.env.example](.env.example)의 접속 설정을 사용합니다. [읽기 권한](docs/BACKEND.md#collector와-postgresql-경계)과 [카테고리 기본값 및 비교 계약](docs/BACKEND.md#홈-쿠팡-베스트-상품-조회)을 참고합니다.

`dbdev` Swagger에서 회원탈퇴를 테스트하려면 테스트용 회원으로 로그인하고 `DELETE /my/account`에 현재 비밀번호를 `password`로 보냅니다. 성공하면 해당 계정의 로그인과 기존 토큰 사용이 차단됩니다.

본인 완료 리포트의 `PATCH /search/reports/{reportId}/inputs`에 `{"cpc": 0}`을 보내 등급을 다시 계산하거나 `{"cpc": null}`로 CPC를 초기화할 수 있습니다. 수정 결과는 리포트 상세에서 확인됩니다.

- API 기본 주소 `http://localhost:8080`
- Swagger UI [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI 3.1 JSON [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

IntelliJ의 Active profiles는 `dbdev`로 지정하고, 기존 DB 비밀번호와 32바이트 이상 Base64 개발 JWT 키를 `CHOZY_DEV_DB_PASSWORD`와 `JWT_SECRET_BASE64`로 주입합니다. 아래 PowerShell 예시는 `.env.dbdev`에 있는 따옴표 없는 `CHOZY_DEV_DB_PASSWORD=값` 항목을 사용합니다. 서버 재시작 후 기존 토큰을 유지하려면 보관한 개발 JWT 키를 다시 주입합니다.

```powershell
$passwordLine = Get-Content -LiteralPath .env.dbdev | Where-Object { $_ -match '^CHOZY_DEV_DB_PASSWORD=' } |
    Select-Object -First 1
if (-not $passwordLine) { throw 'CHOZY_DEV_DB_PASSWORD가 .env.dbdev에 없습니다.' }
$env:CHOZY_DEV_DB_PASSWORD = ($passwordLine -split '=', 2)[1].Trim()
if (-not $env:JWT_SECRET_BASE64) {
    $jwtKey = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($jwtKey) } finally { $random.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKey)
}
# Swagger UI는 같은 origin을 사용합니다. 아래는 별도로 실행하는 프론트엔드용 예시입니다.
$env:CORS_ALLOWED_ORIGINS='http://localhost:3000,http://localhost:5173'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dbdev' '-Dspring-boot.run.arguments=--server.address=127.0.0.1'
```

1. Swagger의 `POST /auth/signup`과 `POST /auth/login`에서 **Try it out → Execute**를 실행합니다. 사용 중인 개발 계정이 있으면 가입을 생략합니다.
2. 로그인 응답의 `data.accessToken` 값만 **Authorize → bearerAuth**에 입력합니다. Swagger가 `Authorization: Bearer <accessToken>` 헤더를 붙입니다.
3. `GET /my/dashboard`나 `GET /home/report-usage`에서 **Try it out → Execute**를 실행합니다. 새로고침하면 Swagger 인증 입력은 유지되지 않습니다.
4. `POST /auth/reissue`는 Access 헤더 없이 본문에 `refreshToken`을 보냅니다. 성공하면 새 Access와 Refresh를 저장하고 Swagger의 **Authorize** 값도 교체합니다. `POST /auth/logout`은 Access 헤더와 Refresh 본문을 모두 받습니다. 비밀번호 변경 성공 후에는 기존 토큰을 지우고 다시 로그인합니다.

CORS는 `CORS_ALLOWED_ORIGINS`에 실제 프론트엔드 origin을 쉼표로 구분해 설정합니다. 스킴·호스트·포트가 정확히 일치해야 하므로 `localhost`와 `127.0.0.1`을 각각 사용한다면 별도로 등록합니다. `Authorization`, `Content-Type`, `Accept` 헤더를 허용하며 쿠키 자격 증명은 사용하지 않습니다. 운영 프로필의 Swagger 기본값은 비활성입니다.

가입 예시:

```json
{
  "loginId": "chozy123",
  "password": "ExamplePassword123!",
  "nickname": "초보셀러",
  "email": "chozy123@example.com",
  "termsAgreed": true,
  "privacyAgreed": true
}
```

일반 가입은 본인인증 없는 임시 개발 경로입니다. dbdev만 기본 허용하고 database는 기본 차단, prod는 설정을 켜도 서버에서 차단합니다. 본인인증/DI·카카오·네이버 로그인은 미구현입니다. 로그아웃은 Refresh를 폐기하며 기존 Access는 잔여 만료 시간까지 유효합니다. 정책·미확정 계약·V3 저장 구조는 [BACKEND](docs/BACKEND.md#일반-계정-인증)를 참고합니다.

`PATCH /my/password` 변경 성공 후에는 기존 Access와 Refresh 모두 차단됩니다. 홈·마이페이지의 무료 정밀분석 한도는 `FREE_MONTHLY_REPORT_LIMIT`에서 설정하며 기본값은 3입니다. 자세한 저장·집계 정책은 [BACKEND](docs/BACKEND.md#마이페이지와-무료-정밀분석-사용량)를 참고합니다.
