# Chozy 백엔드 구조와 개발 규칙

## 문서와 설정의 역할

실행·IntelliJ·환경변수·빌드 방법은 [README](../README.md)를 따른다. 이 문서는 아키텍처와 개발 규칙을 관리한다. 현재 진척과 다음 작업은 [CONTEXT](CONTEXT.md)에 기록한다. 작업별 결과는 Git에서 제외한 `temp.md`에 최신 내용으로 덮어쓴다.

설정은 `application.yml`을 공통값으로, `application-{profile}.yml`을 환경 차이로 관리한다. 비밀값은 환경변수로 주입하며 `.env` 자동 로드를 가정하지 않는다. 기본/일반 테스트 환경은 DB 없이 실행한다. DB 접속은 격리 개발용 `dbdev` 또는 별도 검토한 `database/prod`에서 활성화한다.

기본 `ddl-auto=none`, DB 프로필은 `validate`, SQL init은 never다. Hibernate는 테이블을 생성하지 않는다. Flyway 의존성은 도입했으며 `dbdev`에서만 startup migration을 허용한다. `database/prod`는 disabled이고 외부 설정으로 켜도 연결 전 안전 검증에서 거부한다. JWT는 Spring Security OAuth2 JOSE의 Nimbus 구현을 사용한다. Actuator는 미도입이다.

CORS는 `CORS_ALLOWED_ORIGINS`에 정확한 HTTP(S) origin을 쉼표로 설정한다. 기본은 빈 목록, wildcard·경로·사용자정보는 거부한다. Bearer 헤더 방식의 Stateless 구성을 전제로 credentials는 false이고 CSRF/form login/basic login/session/request cache를 사용하지 않는다. 쿠키 인증을 도입하면 CSRF·CORS 정책을 다시 검토한다. SQL/바인딩 로그는 제한하고 요청 본문·Authorization·예외 원문을 로깅하지 않는다.

## 패키지와 의존 방향

단일 애플리케이션의 기능별 모듈형 모놀리스다. 아래 기능 패키지는 **설계 경계**이며 API 구현 시 필요한 파일부터 만든다.

| `com.chozy.server` 하위 | 책임 |
| --- | --- |
| auth | 가입·로그인·토큰 수명·본인인증 |
| home | 완료 추천·인기 키워드 응답 조합 |
| screening | 키워드 스크리닝·쇼핑 반응·최근 기록 |
| analysis | URL 판별·실시간 정밀분석 요청과 진행 상태 |
| report | 저장 리포트·최근 정밀분석 기록·입력 보완·재계산·의견 |
| favorite | 관심상품·판매 상태·판매 결과 |
| notification | 사용자 알림·읽음 상태·설정 |
| member | 프로필·비밀번호 변경·동의·탈퇴 |
| usage | 월 사용량과 한도, 홈/마이페이지 공통 값 |
| integration.collector | 완료 발행 조회 JDBC/SQL과 외부 데이터 변환 |
| integration.external | 사용자 요청 시 네이버·쿠팡 등 호출과 공동 예산 연계 |
| integration.ai | 계약 확정 후 필요한 AI 연동 |
| common.response/error | HTTP envelope·예외 및 코드 |
| common.security/config | 접근 정책·JWT 필터·CORS·OpenAPI |

`기능.controller → 기능.service → 기능.repository` 방향을 따른다. Controller는 HTTP/검증/DTO 변환, Service는 유스케이스·트랜잭션·사용자 권한, Repository는 저장/조회만 담당한다. 자체 데이터에는 JPA, Collector 발행 조회에는 JDBC를 우선 사용한다. 각 기능에 필요한 경우에만 dto/entity 등의 하위 패키지를 만든다. 전역 controller/service/repository, BaseService, 형식적 인터페이스는 추가하지 않는다.

DTO는 API 입력·출력 계약이며 Entity를 그대로 반환하거나 요청 DTO로 바인딩하지 않는다. 다른 도메인의 Repository/Entity에 직접 의존하기보다 해당 도메인의 필요한 Service 기능을 통해 접근한다. 외부 HTTP/SQL 형식을 업무 Service에 흩뿌리지 않는다. common에는 업무 점수·사용량·관심상품 규칙을 넣지 않는다.

## 응답·예외·보안

성공은 `ApiResponse<T>(status,message,data)`로 반환한다. 생성 API는 HTTP 201과 envelope.status=201을 함께 지정한다. 삭제/로그아웃처럼 데이터가 없는 명세는 data=null을 유지한다. 전역 성공 응답 자동 wrapping은 하지 않는다.

오류는 기존 명세 예시의 `message/status/errors/code`를 사용한다. C001~C012, A003~A006은 원문 표의 상태/메시지와 같다. Bean Validation C001, 타입 C005, 누락 파라미터 C008, JSON C009, 메서드 C007, 미디어 C010, 경로 부재 C003, 업무 충돌 C002, 권한 C006, 예상 밖 오류 C004를 공통 처리한다. C011 업로드/C012 비동기 기반 매핑도 제공한다. 오류 응답에 내부 메시지·SQL·stack trace·입력값을 넣지 않는다. 예기치 않은 예외 로그는 타입만 남긴다.

공개 업무 경로는 HTTP 메서드와 정확한 경로로 허용한다. 나머지 업무 경로는 인증이 필요하다. `common.security.JwtAuthenticationFilter`는 Bearer Access Token의 서명과 만료를 확인한 뒤 회원의 현재 자격 증명 버전을 검증한다. `@AuthenticationPrincipal AuthenticatedMember`에서 회원 ID를 얻고 Service에서 자원 소유권을 검증한다. 잘못된 Authorization은 공개 API에서도 오류를 반환한다. OpenAPI의 bearerAuth는 로그아웃과 3단계 인증 API에 적용했다. offline/dev는 DB 의존 업무 빈 없이 기동한다.

OpenAPI 3.1은 MVC 매핑과 실제 DTO에서 요청·성공 스키마를 생성한다. `@OpenApiContract`는 공통 오류와 API별 오류를 문서에 추가하고 기존 `ErrorCode`에서 예시를 생성한다. `dbdev`는 업무 API 문서, `dev`는 DB 없는 공통 문서를 공개한다. `offline/database/prod`는 기본 비활성이다. Swagger UI 인증은 현재 페이지에서만 유지한다. 접속·프론트엔드 설정은 [README](../README.md#프론트엔드-api-테스트)를 따른다.

API를 구현할 때 **HTTP 메서드 + 정확한 경로**를 SecurityConfiguration에 등록한다. `/auth/**`, `/home/**`, `/search/**` 전체 공개는 금지한다. 현재 명세 분류:

- 공개: POST `/auth/signup`, `/auth/login`, `/auth/reissue`; GET `/auth/check-login-id`.
- 공개 조회: GET `/home/products/recommended`, `/home/keywords/naver`, `/home/keywords/coupang`, `/home/keywords/compare`, `/search/categories`, `/search/coupang-categories`.
- 공개 처리: POST `/search/products/resolve-url`, `/search/screenings/{screeningId}/shopping-analysis`.
- POST `/search/screenings`는 비회원에게 공개하며 JWT 전달 시 회원 기록을 저장한다. 선택 인증 경로에서도 유효하지 않은 JWT는 A004/401, 만료된 토큰은 A003/401을 반환한다.
- 인증: 로그아웃, `/home/report-usage`, 알림, 최근 기록, 분석 요청/상태, 리포트, 관심상품, 마이페이지. prefix 전체 공개에 섞지 않는다.

## Collector와 PostgreSQL 경계

Collector의 `products/product_options/product_history/product_events`는 최신 상태/옵션/실제 변경 관측/출처 있는 사건이며 Spring 소유가 아니다. 점수 큐·산식·수집·완료 증표·발행은 Collector 책임이다. Spring에서 점수 산식을 복사하거나 홈 점수를 재계산하지 않는다. NULL/미확인/0을 구분하고 DOMEME/OWNERCLAN을 합쳐 순위 매기지 않는다.

완료 추천은 검토된 스키마의 SQL `read_supply_home(source,size)`를 우선 사용한다(size 1~1000, source DOMEME/OWNERCLAN). JSONB의 완료 batchId/asOf/formulaVersion/items를 보존하고 products 현재값과 재결합하지 않는다. 완료본 없음(SQL NULL)과 완료본의 빈 items를 구분한다.

쿠팡은 `coupang_API/persistence/best.py:read_published`, 네이버는 `naver_API/queries.py:read_published/read_compare`가 실제 조회 계약이다. Python 함수는 Spring에서 직접 호출할 HTTP 서버가 아니다. 향후 해당 SQL 의미를 JDBC로 구현하고 필요한 읽기 권한을 별도 확인한다. 한 SQL로 최신 완료 배치 전체를 고르고 선택 카테고리/테마가 없다고 과거 데이터를 섞지 않는다. 비교는 네이버와 같은 날짜의 완료 쿠팡만 사용한다. 현재 쿠팡/네이버 Spring runtime 권한은 확정되지 않았다.

Spring 사용자/인증/리포트/관심상품/알림/사용량 데이터는 `chozy` 소유다. `db/migration/chozy`와 `chozy.flyway_schema_history`는 Collector Python migration/ledger와 독립적이다. 운영에서는 전용 migration 관리자와 비소유 Spring runtime 로그인을 구분하며 runtime에는 DDL 권한을 주지 않는다. 운영 Flyway 적용은 별도 검증·승인 작업이며 애플리케이션 startup에서는 disabled다. 필요 시 자체 저장과 Collector 조회에 별도 DataSource/계정을 둔다. Collector 관리자 계정을 재사용하지 않는다.

**2026-11-22 문서 기준** 운영에는 stage7_supply_scoring, stage8_operations, stage7_history_policy_v2, stage10_scoring_performance 네 migration이 미적용이다. runtime_budget_guard·runtime 권한 분리·실제 Spring 로그인/DSN 전환도 미완료다. 사본 권한 검증은 운영 적용 완료가 아니다. read_supply_home/reserve_api_call의 원본은 INVOKER이고 access.py의 DEFINER/고정 search_path 권한 계획은 운영 미적용이다. EXECUTE만 부여하면 충분하다고 가정하거나 관리자 계정으로 우회하지 않는다. 1,500만 성능·용량과 지속 수집 SLA도 미검증이다.

실시간 요청 호출은 정기 발행 조회와 별개다. 실제 계정/API별 공동 `reserve_api_call`/차단 계약과 권한을 먼저 확인한다. 로컬 카운터로 대체하거나 usage 초기화/차단 해제로 우회하지 않는다. 주소·인증·요청/응답 계약이 미정인 Collector HTTP/AI 클라이언트와 외부 설정 bean은 미리 만들지 않았다. 계약 확정 시 `@ConfigurationProperties`+검증, 환경변수 비밀값 주입, timeout/오류 매핑을 통합 경계에 추가한다.

## 홈 공급 조건 우수 상품 조회

- 공개 `GET /home/products/recommended`는 필수 `source`(`DOMEME`, `OWNERCLAN`)와 선택 `size`(기본 10, 1~1000)를 받는다. 필수값 누락은 C008/400, 타입과 소싱처 코드 오류는 C005/400, 크기 범위 오류는 C001/400이다.
- `RecommendedProductReader`는 한 소싱처의 최신 완료 발행본에서 요청한 개수까지 순위순으로 읽는다. HTTP 응답은 발행본의 `asOf`를 `updatedAt`으로 반환하고 순위, 미확인 지표의 null, 실제 측정값 0을 유지한다. 조회 결과에 발행본이 없으면 `updatedAt: null, items: []`를, 상품이 없는 완료 발행본은 `items: []`와 해당 발행본 시각을 반환한다. 두 경우 모두 200이다.
- 발행본 조회 제공자가 미등록이거나 데이터 소스를 사용할 수 없으면 H001/503을 반환한다. Collector SQL 연결은 실제 데이터 연동 단계에 둔다.

## 홈 쿠팡 베스트 상품 조회

- 공개 `GET /home/keywords/coupang`은 선택값 `categoryId`를 양의 정수로 받는다. 범위 오류는 C001/400이고 형식 오류는 C005/400이다. 날짜는 원본 명세에 따라 요청값으로 받지 않으며, 스냅샷 날짜 기준 최신 완료 발행본의 발행 시각을 `updatedAt`으로 반환한다.
- `CoupangBestReader`는 카테고리와 순위순 상품을 동일 완료 발행본에서 읽는다. 카테고리는 ID 오름차순으로 반환하고 미선택 시 첫 카테고리를 기본으로 선택한다. 요청한 ID가 그 발행본에 없으면 요청 ID와 발행 시각을 유지하고 상품만 빈 배열로 반환한다. 완료 발행본이 없으면 두 목록을 빈 배열로, `updatedAt`과 `selectedCategoryId`를 null로 반환한다.
- 순위 변동은 발행본 값을 유지한다. 전일 비교 기준이 없으면 `previousRank`, `rankChange`, `rankStatus`는 모두 null이다. 전일 발행본은 있지만 해당 상품이 전일 순위에 없으면 `rankStatus`는 `NEW`다. 조회 제공자가 미등록이거나 데이터 소스를 사용할 수 없으면 H002/503을 반환한다.

## 홈 네이버 시즌 키워드와 쿠팡 비교

- 공개 `GET /home/keywords/naver`와 `GET /home/keywords/compare`는 선택값 `themeCode`를 양의 정수로 받는다. 최신 완료 네이버 발행본을 기준으로 코드가 가장 작은 테마를 기본 선택하고 순위순 최대 10개를 사용한다. 요청한 테마가 발행본에 없으면 요청 코드와 빈 항목을 반환한다. 발행본 자체가 없으면 갱신 시각과 선택 코드는 null이다.
- 비교는 같은 기준일이며 상품이 있는 완료 쿠팡 발행본에서 키워드별 고유 일치 상품 수를 사용한다. 1개 이상과 0개를 각각 `matchedKeywords`와 `unmatchedKeywords`로 반환하고 두 발행 시각 중 나중 값을 사용한다. 비교할 쿠팡 발행본이 없으면 두 목록을 비우고 안내를 반환한다. 불완전한 일치 건수와 미등록 조회 제공자는 H003/503이다.

## Docker 연결 계획

기존 Collector Compose는 postgres:16, 컨테이너 product-data-collector-postgres, 호스트 `${POSTGRES_PORT:-5432}:5432`, named volume postgres_data와 기본 Compose 네트워크를 사용한다. collectors/operations는 선택 프로필이며 기본 수집 시작이 아니다. 1단계에서는 Docker 메타데이터를 읽기 확인하고 Spring 개발 Compose만 실행했다. Collector DB 접속·설정·리소스 변경은 하지 않았다.

배포 시 실제 기존 네트워크 이름을 읽기 전용 확인한 뒤 Spring 서비스만 해당 external network에 연결해 postgres:5432를 사용한다. 네트워크 이름을 추측하거나 PostgreSQL/volume을 중복 정의·교체하지 않는다. 호스트에서 실행하는 Spring은 검토된 호스트 공개 포트를 사용한다. Spring은 독립 포트 8080을 기본으로 하며 공개 포트 충돌·권한 전환·DB 준비 상태를 배포 때 확인한다. 운영 Dockerfile/Compose는 배포 단계에 작성한다. 격리 개발 DB의 Compose는 이 배포 연결과 별개다.

## Spring DB 기반과 회원 정책

### 환경과 Migration 경계

`compose.dev.yml`의 프로젝트는 `chozy-spring-dev`, 컨테이너는 `chozy-spring-dev-postgres-1`, volume은 `chozy-spring-dev_spring_dev_data`, network는 `chozy-spring-dev_spring_dev`다. PostgreSQL 16을 `127.0.0.1:55432`에만 공개한다. Collector의 5432·`product-data-collector_postgres_data`·`product-data-collector_default`와 연결하거나 공유하지 않는다. stop/down 시 volume은 보존하며 `down -v`는 사용하지 않는다. 이 개발용 관리자 계정은 격리 개발에 한정하며 운영 권한 모델로 복제하지 않는다.

실행·연결·검증 명령은 [README](../README.md#격리-개발-db-실행)에 둔다. 개발 연결 정보는 `CHOZY_DEV_DB_NAME`, `CHOZY_DEV_DB_USERNAME`, `CHOZY_DEV_DB_URL`, `CHOZY_DEV_DB_PASSWORD` 환경변수로 관리한다. DB명·사용자·로컬 주소에는 안전한 기본값이 있으며 안전 검증은 문서의 canonical 대상만 허용한다. 비밀번호 기본값은 없다. 운영 접속 환경변수 `CHOZY_DB_*`와 분리한다. `dbdev`의 datasource URL/사용자가 달라지거나 offline/dev/prod/database와 혼용하면 bean 생성 전에 실패한다. Flyway 별도 URL/계정 주입도 거부한다. 접속 실패는 Hikari 제한 시간과 초기 JPA/Flyway 검증에서 기동 실패로 처리하며 offline으로 자동 전환하지 않는다.

Flyway core와 PostgreSQL 모듈은 Boot BOM 버전을 사용한다. `V1__create_chozy_schema.sql`은 스키마 경계, `V2__create_members.sql`은 회원 테이블을 담당한다. Flyway가 history 배치를 위해 스키마를 먼저 만들므로 V1은 `IF NOT EXISTS`다. 이력은 `chozy.flyway_schema_history`에 checksum·버전·성공 여부로 관리한다. validate-on-migrate=true, baseline-on-migrate=false, clean-disabled=true다. 적용된 SQL을 수정하지 말고 V3 이후 migration을 추가한다. Collector `schema_migrations`와 기존 DDL은 참조하거나 수정하지 않는다. 운영 서버 공동 사용 여부와 관계없이 스키마·소유자·runtime 권한을 분리해야 하며 이번 단계는 운영 적용을 하지 않는다.

### 최소 회원 데이터와 중복 정책

`chozy.members`는 API의 숫자 userId에 대응하는 bigint identity PK, login_id, password_hash, nickname, email, 필수 약관/개인정보 동의 시각, 생성/수정 시각, nullable verified_di_hash/di_verified_at을 갖는다. password_hash에는 PasswordEncoder 결과만 저장한다. 원문 비밀번호·전화번호·원본 DI는 저장하지 않는다. `updated_at`은 프로필 수정 서비스에서 갱신한다. 약관 버전·탈퇴/재가입·보관 정책은 운영 전 확정해야 한다.

- loginId: 앞뒤 Unicode 공백 제거 후 Locale.ROOT 소문자화. 저장값은 ASCII `[a-z0-9][a-z0-9_.-]{0,99}`로 제한한다. 정규화된 값에 Unique 제약을 두며 비정규 저장은 CHECK로 거부한다. 이는 이번 DB 정책이며 원본 명세에는 길이/문자/대소문자 정책이 없으므로 2단계 API 검증과 UI 계약을 맞춘다.
- email: 앞뒤 공백 제거 후 전체 주소 소문자화, 내부 공백 불허. DB는 소문자 canonical 값·최소 @ 구조와 Unique를 강제한다. API의 @Email DTO 검증과 함께 적용한다. 로컬 파트도 대소문자를 구분하지 않는 서비스 정책이며 공급자별 점 제거·plus alias 병합은 하지 않는다. 수정 시에도 같은 정책과 Unique를 적용한다. 국제화 주소 지원 범위는 후속 확정한다.
- 사전 중복 확인 API는 사용자 안내용이다. 동시 가입의 최종 판정은 DB Unique다. 서비스의 제약 위반 트랜잭션은 rollback하며 Unique 충돌은 C002로 변환한다.
- DI: 미인증 회원의 두 필드는 모두 NULL이다. 두 필드가 함께 있을 때만 허용하고 verified_di_hash에는 Unique를 적용한다(NULL 여러 개 허용). 컬럼은 32~128자의 URL-safe 문자열을 임시 저장 계약으로 예약했으며 해시 알고리즘을 확정한 것이 아니다. DB는 인증 진위를 판정할 수 없다. 신뢰한 업체 검증 결과만 서버 내부에서 기록해야 하며 현재 Entity에는 DI 입력 경로가 없다. 통합 테스트의 합성 문자열은 폐기되는 테스트 DB의 제약 fixture일 뿐 개발 DB에 인증 회원을 만들지 않는다.
- 본인인증이 없으면 서로 다른 아이디와 이메일을 쓰는 동일인의 중복 가입까지 막을 수 없다. Unique 제약과 실제 본인인증은 별개이며 전화번호 일치만으로 동일인이나 DI로 취급하지 않는다.

회원 Entity와 Repository는 API DTO와 분리한다. 가입과 회원 관리의 트랜잭션은 Spring JPA transaction manager를 기반으로 Service 유스케이스 경계에 둔다. Collector의 완료 추천·시장 snapshot 조회는 JDBC/SQL 경계로 유지한다.

### 미구현 인증과 원본 차이

기획서 2.1(인쇄 4쪽)은 휴대폰 본인인증 필수·DI 해시 기반 1인 1계정과 소셜 첫 로그인에서도 인증을 요구한다. 반면 회원가입 API 원본은 loginId/password/nickname/email/termsAgreed/privacyAgreed만 받으며 인증 결과 계약이 없다. 기획서는 소셜 가입 시 아이디·비밀번호가 없다고 명시하지만 현재 테이블은 로컬 계정용 필수 컬럼이다. 소셜 구현 시 credential 분리 또는 nullable 정책 migration을 검토해야 한다. 원본 자료는 보존하고 이번 단계는 아래 개발용 임시 가입 정책을 적용한다. 운영 가입은 본인인증 계약 보완 후 구현한다.

1. 휴대폰 본인인증 API 연동·DI 발급/검증은 미구현이다. 업체 선정, 실제 DI 응답 필드·사이트 범위·재사용/만료/서명 검증 계약을 확인해야 한다.
2. 원본 DI를 저장하지 않는 키 기반 해시(HMAC 등), key 보관/버전/회전, canonical 인코딩을 검토하고 업체 선정 후 확정한다. 임시 해시를 생성해 인증값으로 사용하는 경로는 없다.
3. 동일 DI의 1인 1계정 보장과 기존 회원 연계/충돌/탈퇴 후 재가입 정책을 정해야 한다. 가입 요청의 미검증 DI를 신뢰하거나 아이디 중복 검사만으로 1인 1계정을 보장했다고 처리하지 않는다.
4. 카카오·네이버 로그인과 기존 계정 연결은 미구현이다. 후속 외부 계정 테이블에는 `(provider, provider_user_id)` Unique가 필요하다. 동일 DI로 검증된 회원은 별도 계정 생성보다 기존 회원 연결을 우선 검토하되 본인 확인과 계정 탈취 방지 정책을 확정한다. 이메일/전화번호 일치만으로 자동 연결하지 않는다.
5. 일반 회원가입·로그인·JWT·Refresh Token 저장/재발급·로그아웃은 2단계에서 구현했다. 이메일 인증·외부 인증 서비스는 미구현이다. 기획서에서는 이메일 인증을 삭제하고 알림용 필수 입력으로 변경했으므로 이메일 인증 도입 여부도 별도 결정한다.

### 검증 원칙

기본 Maven verify는 공통 회귀·DB 설정 안전 검증·JWT·가입 정책을 DB 없이 실행한다. `-Pdb-test verify`는 Testcontainers가 생성한 PostgreSQL 16 URL만 사용하고 Flyway를 적용한 뒤 JPA validate·저장/조회를 확인한다. 운영/개발 DB URL은 테스트 입력으로 받지 않는다. Unique/동시 요청·이메일 canonical CHECK·nullable DI와 합성 DI Unique·중복 실패 후 전체 rollback 및 연결 복구·Migration 재실행을 검증한다. 테스트 데이터는 폐기 컨테이너에만 저장하고 실제 개발 DB를 truncate/clean하지 않는다. Docker가 없을 때 성공으로 건너뛰지 않는다.

## 자료 차이와 미확정 사항

- 서버 참고 자료는 PDF 1개와 Markdown 41개(실제 URL이 있는 API 39개, 템플릿 2개)다. 공통/인증 에러 표는 서버 참고 폴더에 없어 Collector `docs/references/API 명세서/에러 코드` 원본을 읽었다. 원본 자료는 수정하지 않았다.
- 오류 envelope는 빈 템플릿의 A007 예시에만 명시되어 있다. errors 항목의 세부 필드/값은 정의되지 않아 빈 배열로 유지한다. 새 traceId/timestamp/path 필드를 만들지 않았다. A007은 코드 표에 없어 등록하지 않았다.
- 토큰 미제공에 대한 별도 에러 코드가 없어 현재 인증 진입 실패는 A004를 사용한다. Access 만료 A003, Refresh 만료 A005, 누락·서명/형식/유형 오류 A004를 적용한다. 406 등 표에 없는 HTTP 오류 정책은 추가 확정이 필요하다. 현재 미분류 MVC 오류는 C004로 안전하게 처리한다.
- 기획서의 휴대폰 본인인증 필수/DI 해시 정책이 회원가입 DTO 명세에는 빠져 있다. 운영 가입 구현 전에 업체 계약·필드와 정합성을 맞춘다. 소셜 로그인/이메일 발송 계약도 미정이다.
- 기획서의 과거 수집/호출 가정은 최신 Collector CONTEXT/DATABASE/API_CONTRACTS와 실제 코드의 운영 상태를 대체하지 않는다. 예: 도매매 Private 승인/지속 처리, 90일 coverage, 운영 권한 전환은 완료로 보지 않는다.

## 코드와 테스트 배치

Java는 UTF-8, 공백 2칸을 사용하며 Maven XML은 공백 4칸을 사용한다. `.editorconfig`를 기준으로 IDE 설정을 맞추고, `.gitattributes`로 Windows 실행 스크립트는 CRLF, 소스와 Unix Wrapper는 LF를 유지한다. 참고 원본은 포맷 대상으로 삼지 않는다.

테스트 패키지는 검증하는 운영 코드의 패키지를 따른다. 애플리케이션 기동 검증은 루트의 `ChozyServerApplicationTest`, 공통 응답/예외/보안 검증은 `common`의 각 책임 아래에 둔다. 여러 책임을 하나의 FoundationTest에 계속 쌓지 않는다. 테스트 fixture는 `support`에 두고 `@TestComponent`로 일반 스캔에서 제외한 뒤 필요한 테스트에서만 명시적으로 가져온다. 업무 API가 추가되면 해당 기능의 같은 패키지에 테스트를 추가한다.

## 검증과 다음 API 개발

다음 구현 범위는 [서버 개발 순서](CONTEXT.md#개발-순서)를 따른다.

`./mvnw.cmd -B verify`는 외부 API·DB 없이 공통 응답, MVC 오류, 민감정보 비노출, 보안 차단, CORS, OpenAPI와 DB 인프라 부재를 검증한다. 테스트 전용 Probe Controller는 src/test에만 두며 배포 JAR에는 포함되지 않는다. 데이터/외부 시스템 통합 검증은 별도 격리 환경과 명시적인 자격증명으로 추가한다. 운영 DB로 실패를 해결하지 않는다.

다음 API는 원문 method/path/request/response/JWT부터 확인하고 해당 기능의 Controller·DTO·Service·필요한 Repository만 추가한다. 인증 규칙과 소유권, 실제 HTTP 상태/envelope 일치, 정상/검증/인증/권한 오류 테스트를 함께 작성한다. 자체 데이터 변경은 전용 migration으로 리뷰한다. 완료 발행 조회와 실시간 호출은 별도 통합 책임으로 유지한다. 구현하지 않은 API를 문서나 코드에 엔드포인트로 등록하지 않는다.

버전 근거: [Spring Boot 3.5 공식 문서](https://docs.spring.io/spring-boot/3.5/), [springdoc 호환성 안내](https://springdoc.org/v2/), Maven Central의 실제 배포 artifact 확인. Boot 3.x 요구를 유지했으며 배포 전 해당 계열 지원 정책과 보안 패치를 재검토한다.


## 2단계 일반 계정 인증 구현

### API와 패키지

| Method / Endpoint | 성공 상태 | data |
| --- | --- | --- |
| POST `/auth/signup` | 201 | userId, loginId, nickname, email, createdAt |
| GET `/auth/check-login-id?loginId=...` | 200 | loginId, available |
| POST `/auth/login` | 200 | userId, nickname, accessToken, refreshToken, tokenType, expiresIn |
| POST `/auth/reissue` | 200 | accessToken, refreshToken, tokenType, expiresIn |
| POST `/auth/logout` | 200 | null |

요청 필드·성공 메시지는 references의 각 API 원본을 따른다. 가입은 6개 필수 필드, 로그인은 loginId/password, 재발급·로그아웃 body는 refreshToken이다. 로그아웃만 Access Bearer 인증 필수다. 응답은 공통 envelope를 유지하며 expiresIn은 Access 수명(초), tokenType은 Bearer다. createdAt은 UTC ISO-8601 Instant이며 명세 DateTime의 동일 시각을 표현한다.

`auth/controller`는 HTTP/검증, `auth/dto`는 요청·응답, `auth/service`는 가입·로그인·Rotation·무효화와 트랜잭션, `auth/entity`와 `auth/repository`는 Refresh 저장을 담당한다. 회원 Entity/Repository는 기존 `member`에 유지했다. 공통 JWT 서명·검증/인증 주체/필터는 `common.security`, SQL Unique 충돌 변환은 `common.error`에 둔다.

### 개발용 임시 가입 정책과 중복 방지 범위

현재 회원가입은 **휴대폰 본인인증 없는 개발·테스트용 임시 경로**다. `app.auth.unverified-signup-enabled` 기본값은 false이며 dbdev 설정만 true다. 격리 테스트는 명시적으로 true를 주입한다. prod 프로필에서는 이 설정을 true로 덮어써도 Service가 C006/403으로 차단한다. database 프로필도 기본 차단한다. 운영 배포는 prod를 사용하며 일반 가입 스위치를 켜서 본인인증을 우회하지 않는다. 프론트엔드 숨김과 독립적으로 서버가 강제한다.

본인인증 성공을 흉내 내거나 DI를 생성하지 않는다. 새 회원의 verified_di_hash/di_verified_at은 모두 NULL이다. loginId/email은 strip 후 Locale.ROOT 소문자로 정규화한다. 아이디는 기존 DB와 동일하게 `[a-z0-9][a-z0-9_.-]{0,99}`, 닉네임은 strip 후 1~100자, 이메일은 @Email 및 최대 320자를 검증한다. 비밀번호는 trim하지 않고 8자 이상·최대 72 UTF-8 바이트로 제한하며 BCrypt cost 12로 해시한다. login은 기존 비밀번호 검증을 위해 별도 최소 길이를 강제하지 않는다. 두 필수 동의는 null/false 모두 C001이다. 이 길이·문자 정책은 API 원문에 상세 제한이 없어 기존 DB와 BCrypt 한계를 바탕으로 정한 **서버 구현 정책이며 프론트 계약 보완 대상**이다.

중복 확인은 가입 예약이 아니다. 가입은 트랜잭션 내 사전 조회 후 실제 saveAndFlush로 DB Unique 제약을 확인한다. 동시 요청의 최종 승자는 DB가 정하며 PostgreSQL 23505는 C002/409, 그 밖의 무결성 실패는 C004로 처리한다. 정규화된 아이디/이메일 중복은 방지하지만 서로 다른 아이디·이메일을 쓰는 동일인 중복 가입은 방지하지 못한다. 기존 검증된 DI 해시 Unique는 유지하며 **1인 1계정 구현 완료가 아니다.**

### JWT와 Refresh 저장·수명

HS256만 허용하며 환경변수 `JWT_SECRET_BASE64`에 CSPRNG로 생성한 32바이트 이상의 키를 Base64로 넣는다. DB 프로필은 키 미제공·잘못된 인코딩·짧은 키·0 이하 수명에 기동 실패한다. 개발/운영 기본 비밀키는 없다. 키·비밀번호·토큰 원문은 로그/오류 응답에 넣지 않고 민감 DTO의 toString도 감춘다.

issuer=chozy, sub=회원 ID, type=access/refresh, 임의 UUID jti, iat/exp를 서명한다. 서명/알고리즘/issuer/유형/필수 claim/시각을 검증하며 시계 유예는 없다. `JWT_ACCESS_SECONDS` 기본 3600(1시간), `JWT_REFRESH_SECONDS` 기본 1209600(14일)이다. 운영은 TLS 및 시간 동기화가 필요하며 키 변경은 기존 토큰을 무효화한다. 키 버전/무중단 교체 정책은 후속 과제다. 외부 인증 서버·Redis는 도입하지 않았다.

V1/V2는 변경하지 않았다. V3 `V3__create_refresh_tokens.sql`은 chozy.refresh_tokens(id, member_id FK, token_hash UNIQUE, expires_at, created_at, revoked_at)와 회원/만료 인덱스를 추가한다. 고엔트로피 서명 토큰의 SHA-256 해시만 저장하며 원문은 응답 시 전달한다. 로그인마다 별도 토큰을 생성해 복수 기기 로그인을 허용한다. 서버 재시작 후에도 같은 키와 DB로 검증한다.

재발급은 Access 없이 Refresh의 서명·만료·유형 및 저장 해시/소유자/유효 상태를 확인한다. PESSIMISTIC_WRITE 행 잠금과 단일 트랜잭션으로 이전 토큰 revoke와 새 토큰 저장을 원자적으로 수행한다. 동시 재발급은 하나만 성공하며 이전 토큰 재사용은 A004다. 재사용 시 후속 토큰까지 자동 폐기하는 family 정책은 적용하지 않았다. 로그아웃은 Access 회원 ID와 Refresh 소유자를 대조한 뒤 같은 행 잠금으로 무효화한다. 타인 토큰은 C006이며 해당 토큰은 보존된다.

일반 로그아웃은 해당 Refresh를 폐기하며 기존 Access는 남은 수명(기본 최대 1시간) 동안 유효하다. 비밀번호 변경은 3단계에서 별도 회원 단위 무효화를 적용했다. 만료·폐기 행은 감사와 재사용 검증을 위해 보존하며 만료 인덱스로 정리 작업을 확장할 수 있다. 보존 기간과 정기 삭제는 운영 전 확정한다.

### 오류 계약 보완

Access 만료=A003/401, Refresh 만료=A005/401, 위조·잘못된 형식·서명·유형·누락 인증=A004/401이다. 로그인 ID 부재와 비밀번호 불일치는 동일 A004 응답이며 부재 계정에도 dummy BCrypt 검증을 수행한다. Refresh 미등록/재사용/로그아웃 폐기도 인증 정보가 유효하지 않으므로 기존 의미에 맞는 A004를 사용한다. 중복=C002, 입력=C001, 타인 Refresh 및 운영 가입 차단=C006이다. A006은 SMS 코드 의미이므로 재사용하지 않았다. 원본에는 로그인 실패/Refresh 재사용/가입 차단 전용 코드가 없으며 **전용 코드를 확정한 것으로 취급하지 않는다.** 향후 프론트와 세부 코드 계약을 보완한다.

### 미구현과 후속 연결

- 휴대폰 본인인증 업체 선정, SMS 발송/검증, 실제 DI 검증·저장 API는 미구현이다. 업체의 실제 결과를 검증하고 안전하게 해시한 DI로 기존 회원을 조회해 중복 생성을 차단해야 한다. 검증된 DI 기반 1인 1계정은 이 연동 뒤 완성한다. 가입 API에 인증 결과 필드가 없는 기획/명세 차이를 해소해야 한다.
- 기존 미인증 일반 계정의 연결·전환, DI 해시 키 관리/교체 및 탈퇴·재가입 보관 정책을 확정해야 한다.
- 카카오·네이버 OAuth, 소셜 연결·해제는 미구현이다. 동일 DI 기반 계정 중복 방지와 기존 회원 연결 정책을 확정하며 이메일/전화번호 일치만으로 타인 소셜 계정을 자동 연결하지 않는다. 소셜 계정에는 비밀번호가 없으므로 password_hash NOT NULL 변경 또는 인증수단 분리 Migration이 필요하다. 이번에는 가짜 클래스·사전 소셜 테이블을 만들지 않았다.
- 이메일 인증과 비밀번호 찾기/재설정은 후속이다. 이메일 인증은 기획서에서 가입 요건으로 제외되어 적용 목적과 계약을 별도 확정한다.

### 회원탈퇴

JWT `DELETE /my/account`는 토큰 회원을 잠근 뒤 현재 비밀번호를 검증한다. 비밀번호가 틀리면 C001/400이다. 성공 시 V11의 `withdrawn_at`과 자격 증명 버전을 갱신하고 모든 Refresh Token을 동일 트랜잭션에서 폐기한다. 로그인·Access 인증·Refresh 회전은 활성 상태를 검증한다. 이미 접수된 분석 작업은 기존 작업 처리 정책을 따른다.

현재 비활성 계정의 식별자와 관련 데이터를 보존한다. 보관 기간과 삭제·비식별화, 동일 식별자로 재가입하는 정책은 공개 전에 확정해야 한다. 현재 Unique 제약은 탈퇴 계정에도 적용한다.
- 다음 단계는 프론트 인증 흐름 연결·명세 보완, 본인인증 계약 선정과 계정 전환 설계, 인증 주체를 사용하는 회원 소유 업무 API 구현이 적절하다. 운영 공개 전 요청 제한과 인증 시도 모니터링도 결정한다.

### 검증 방식

기본 verify는 offline 기동/기존 공통 회귀와 JWT 유형·위조·만료·키 및 가입 허용 정책을 DB 없이 검증한다. db-test는 기존 MemberDatabaseIT를 유지하고 AuthDatabaseIT에서 명세 응답·검증·정규화·해시·중복/동시 가입·로그인·Security 필터·Rotation/재사용/동시 재발급·소유권/로그아웃·Swagger를 검증한다. 테스트 전용 PostgreSQL 컨테이너를 실제 재시작한 뒤 저장 토큰의 재발급까지 검증한다. 개발 DB/회원 데이터를 삭제하거나 Collector DB에 접속하지 않는다. 테스트 컨테이너는 격리 통합 테스트용이며 개발 DB 컨테이너를 중복 생성하는 용도가 아니다.

JWT 구현 참고: [Spring Security 공식 JWT 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html). 현재 구현은 직접 서명/검증 후 기존 공통 오류 응답과 통합한다.

## 3단계 마이페이지와 무료 정밀분석 사용량

| API | 구현 |
| --- | --- |
| GET `/my/dashboard` | 본인 프로필, 한국 날짜 기준 가입 일수, 무료 플랜, 정밀분석·스크리닝·관심상품 사용량, 광고 동의 상태 |
| PATCH `/my/profile` | 닉네임·이메일 부분 수정, 기존 정규화 및 이메일 UNIQUE 적용 |
| PATCH `/my/password` | 현재 비밀번호와 가입 정책 검증, BCrypt 재해시, 기존 Access·Refresh 전부 무효화 |
| PATCH `/my/marketing-consent` | 변경 시각과 동의·철회 이력 저장. 동일 값 요청은 이력과 시각 유지 |
| GET `/home/report-usage` | 대시보드와 동일한 무료 정밀분석 사용량 반환 |

다섯 API 모두 JWT에서 회원을 식별한다. 비밀번호 변경은 `members.credential_version` 증가와 Refresh 일괄 폐기를 한 트랜잭션에 커밋한다. 토큰의 버전이 현재 버전과 일치해야 하며, 기존 버전 claim 없는 JWT는 버전 0에서 계속 사용할 수 있다. 로그인·재발급·로그아웃·비밀번호 변경은 회원 행을 먼저 잠근다. 인증 토큰 발급·폐기가 경합해도 비밀번호 변경 전에 발급된 토큰은 버전 검증에서 차단된다. 로그아웃의 단일 Refresh 폐기 계약은 유지한다.

광고 동의 기본값은 `false`, 최초 변경 시각은 `null`이다. V4는 `members`에 자격 증명 버전과 광고 동의 현재 상태를 추가하며 `marketing_consent_history`에 실제 변경만 누적한다. 광고 동의와 서비스 알림 설정은 독립적인 정책이다. 마이페이지 API 원문에 업무 오류 세부 코드가 없어 기존 공통 계약을 적용했다. 현재 비밀번호 불일치·동일 비밀번호·값 검증은 C001, 이메일 중복은 C002, 잘못된 JSON 형태는 C009, 이전 자격 증명 버전은 A004다. 광고 동의 이력 보관 기간은 개인정보 처리 정책 확정이 필요하다.

### 집계와 후속 정밀분석 연결

V5는 `report_usage_entries`, `member_screening_runs`, `member_favorites`를 추가한다. `ReportUsageService`가 홈과 마이페이지의 공통 집계값을 제공한다. 월간 무료 기본 한도는 `app.usage.free-monthly-report-limit` / `FREE_MONTHLY_REPORT_LIMIT`에서 설정하며 기본값은 3이다. 매월 1일 00:00 KST에 읽는 집계 월이 바뀐다. 이전 달 기록은 유지하며 한도를 낮춘 경우 잔여 횟수의 최솟값은 0이다.

- 정밀분석 구현 시 성공한 **새 리포트**의 저장 트랜잭션에서 `recordCreatedReport(memberId, reportId, dataLabQuotaExhausted)`를 호출한다. PostgreSQL의 기본 READ COMMITTED 트랜잭션을 사용한다. 같은 회원의 차감 검증과 원장 기록은 회원 행 잠금으로 직렬화하고, 리포트 ID의 기본 키로 재시도 중복을 막는다. 정밀분석은 한도 초과 예외가 발생하면 리포트 저장도 롤백해야 한다.
- 실패, 저장 리포트 열기, 옵션 변경과 재계산은 원장에 기록하지 않는다. 데이터랩의 공유 호출 한도가 100% 소진되어 트렌드·지속성을 제외한 리포트는 신뢰할 수 있는 연동 결과로 면제 여부를 전달한다. 면제 기록은 무료 횟수 집계에서 제외한다. 접수·예약 정책은 [정밀분석 작업과 저장](#정밀분석-작업과-저장)을 따른다. 일일 사용량 정책과 데이터랩 예산 연동은 후속 작업이다.
- 스크리닝은 해당 회원의 KST 월 구간에 저장된 실행 행을 센다. 관심상품은 V8 등록 API와 연결되어 해당 회원의 활성 `INTERESTED`와 `SELLING` 등록 행을 센다.

### 검증과 남은 작업

2026-11-23 개발 DB 적용 중 `-Pdb-test verify`를 재실행했다. 기본 테스트 29개와 격리 PostgreSQL 16 통합 테스트 42개가 모두 통과했다. 격리 테스트에서 V2 데이터의 V3~V5 업그레이드, 기존 API 회귀, 회원 격리, 비밀번호 변경과 재발급 경합, 동의 이력, KST 경계, 사용량 한도·중복·면제·롤백을 검증했다.

### 기존 개발 DB 적용 상태

2026-11-24 기존 `chozy_spring_dev`의 V5 시점 PostgreSQL 아카이브를 만들고 목록에서 Flyway 이력 포함 여부를 확인했다. `dbdev` 기동으로 V6~V11을 적용했고 Flyway 성공 이력 11건과 JPA 스키마 검증을 확인했다. 회원·Refresh Token·관심상품·리포트 사용량 원장의 행 수를 적용 전후에 대조했다.

검증 기동 주소는 `127.0.0.1:8080`이다. `dbdev` 설정으로 Swagger UI·OpenAPI JSON·쿠팡 대분류 조회는 200, 미인증 마이페이지 조회는 401을 확인했다. JWT 키는 프로세스 환경에 주입했다.

## 정밀분석 작업과 저장

DB 프로필에서 JWT로 `POST /search/analyses/manual`과 `GET /search/analyses/{analysisId}/status`를 제공한다. V6는 `chozy.manual_analysis_jobs`와 리포트 JSON 스냅샷 저장소를 추가한다. 상태는 회원 ID를 조건으로 조회하며 타인과 없는 ID는 모두 C003/404다.

- 접수는 회원 행 잠금 아래 월 사용량과 진행 중 작업 수를 읽는다. 월 한도 소진은 R001/429, 남은 자리가 모두 예약된 경우는 R002/409다. 다음 갱신 일시는 `GET /my/dashboard`의 `reportUsage.nextResetAt`을 사용한다. 이 개발 계약은 월 한도를 접수에서 먼저 적용하므로 월 한도와 데이터랩 한도가 동시에 소진된 경우도 월 한도에 따라 거절한다. 예약은 차감에 포함하지 않으며 작업 종료 시 해제한다.
- `ManualAnalysisEngine`이 등록된 환경에서만 202/PENDING으로 작업을 저장한다. 미등록 시 R003/503을 반환한다. 작업기는 짧은 트랜잭션에서 `SKIP LOCKED`로 작업을 가져오고 엔진은 트랜잭션 밖에서 실행한다. 점유 시간은 5분이며 기한을 지난 작업은 실패 처리한다. 재요청은 회원이 시작한다.
- 진행 조회는 대기 0, 작업을 가져온 직후 1을 반환한다. 직접 입력 시장 엔진은 기획서 6.1의 키워드 조회 시작(4)과 시장 데이터 조회 시작(5)을 각각 짧은 트랜잭션으로 저장한다. V12는 진행 값이 없는 기존 행과 단계 보고를 하지 않는 엔진에 처리·실패 단계 1을 유지한다. 실패 응답에는 마지막 저장 단계가 남는다. 점유 시간이 지난 작업과 종료된 작업은 진행 값을 갱신할 수 없고, 늦게 들어온 낮은 단계도 무시한다. 완료 7은 리포트와 사용량 원장이 커밋된 뒤 조회된다.
- 완료 상태·리포트·사용량 원장 저장은 한 트랜잭션이다. 기한 내 처리 중인 작업만 완료할 수 있다. 차감 실패 시 리포트도 롤백한다. 실제 데이터랩 한도 소진 시 차감 면제를 전달할 책임은 엔진에 있다.

### 직접 입력 시장 데이터

- opt-in 엔진은 검색광고 `RelKwdStat`에서 키워드와 경쟁 지표를 읽고 쿠팡 파트너스 상품 검색을 사용한다. 시장가는 상품명을 먼저 조회하고 결과가 비면 분석 키워드를 시도한다. 규격·묶음·배송 조건·이상치를 반영한 중앙값과 표본 경고를 저장한다. 데이터랩 일간 56일과 다년 완결 월로 추세·지속성을 계산한다. [검색광고 API](https://naver.github.io/searchad-apidoc/) · [데이터랩 검색어 트렌드 API](https://api.ncloud-docs.com/docs/naver-api-hub-search-trend)
- 초기 리포트와 리포트 수정에 같은 마진·종합점수·등급 산식을 사용한다. 초기에는 쿠팡 대분류 선택이 필요하며 시장가를 산출할 수 없으면 예상 판매가도 받는다. 대분류를 선택하면 카탈로그의 부가세 포함 기본 요율을 적용한다. Wing 카테고리 추천·세부 요율, 목표 순위 입찰가·상표 조회는 후속이다.
- 별도 PostgreSQL 접속에서 `public.reserve_api_call(account, api, true)`을 HTTP 요청 전에 커밋한다. 예산 분류는 쿠팡 `default`, 네이버 `ad`·`trend`다. `public.block_api_account` 준비를 확인하며 공급사 403/429 응답(쿠팡 본문 코드 포함)은 해당 공동 정책을 차단하고 작업을 실패 처리한다. 간격 제한은 최대 두 번 더 예약을 시도한다. 예약된 외부 요청을 앱에서 자동 재시도하지 않는다.
- 데이터랩의 공동 일일 예산 결과가 `exhausted:day`인 경우 트렌드·지속성을 빼고 무료 횟수 면제를 기록한다. 권한·간격·HTTP 오류는 작업 실패로 처리한다. 성공적으로 읽은 응답은 최대 256개를 프로세스별 10분 동안 보관한다.

### 상품 URL 판별과 요청

- 공개 `POST /search/products/resolve-url`은 도매매·도매꾹의 숫자 상품 경로와 오너클랜 `/V2/product/view.php?selfcode=...` 형식을 처리한다. HTTP(S) URL에서 정확한 호스트와 상품 키를 읽고, 조회 제공자에는 정규화된 소싱처와 상품번호만 전달한다. 분석용 URL에는 추적 쿼리와 프래그먼트를 포함하지 않는다.
- 도매꾹은 같은 상품번호의 도매매 상품·옵션을 확인한 뒤 전환한다. 지원하지 않는 링크와 상품이 없다고 확인된 링크는 200과 직접 입력 안내를 반환한다. URL 형식 오류는 C001/400, 상품·옵션 조회 제공자 미구성 또는 조회 불가는 R003/503을 반환한다.
- JWT `POST /search/analyses/url`은 접수 시 같은 판별 절차를 다시 실행한다. 지원되지 않는 링크, 해당 상품에 없는 옵션, 가격이 다른 옵션 중 선택이 누락된 요청은 C001/400이다. 옵션 가격은 최종 도매가 기준이다. 제공된 검색 키워드를 분석 엔진에 우선 키워드로 전달한다.
- V10은 두 입력 방식에 기존 작업 ID와 리포트 ID를 공유하도록 소싱처·상품번호·원본 URL·판별 URL·선택 옵션 ID를 기존 작업 테이블에 저장한다. URL 엔진 등록 후 202/PENDING을 반환하며 같은 회원의 직접 입력과 진행 중 예약을 합산한다. 동일 URL로 새 요청할 수 있고 리포트 생성에 성공한 경우에만 차감한다. 데이터랩 예산 소진 면제와 실패 시 예약 해제는 직접 입력과 같다. URL 엔진은 선택 옵션 상세와 계산된 리포트 내용을 스냅샷으로 반환한다.

## 리포트 조회와 점수 의견

- `GET /search/coupang-categories`는 정밀분석에서 직접 선택할 수 있는 대분류 15개를 반환한다. `CoupangCategoryCatalog`는 Chozy 내부 코드와 명세의 부가세 포함 기본 수수료율(%)을 관리한다. 재계산에서 사용자가 대분류를 선택하면 기본 요율과 안내를 적용하고, 자동으로 결정된 카테고리는 저장된 요율을 사용한다.

- `GET /search/reports/{reportId}`는 본인의 완료 리포트 스냅샷을 단계별 응답 DTO로 변환한다. 타인 리포트와 없는 ID는 C003/404를 반환한다. 직접 입력에는 공급처 전용 필드를 null로 반환한다. 일부 필드만 가진 V6 스냅샷은 저장된 입력과 명세의 시뮬레이션 초기값으로 보완한다. 누락된 시장 지표는 null이고 비어 있는 목록은 빈 배열이다. 종합점수가 없고 계산 대기 상태가 저장되지 않은 경우 `INSUFFICIENT_DATA`를 반환한다.
- `GET /search/analyses/recent`는 직접 입력과 URL 분석의 본인 요청·처리 중·완료·실패 기록을 페이지로 제공한다. 완료된 작업은 완료 시각, 실패한 작업은 종료 시각, 진행 중 작업은 요청 시각을 기준으로 역순 정렬한다. 동률에는 작업 ID 내림차순을 적용한다. `analyzedAt`은 완료된 분석에만 제공한다. 기본값은 `page=0`, `size=10`이고 최대 크기는 100이다. 건수와 페이지는 동일한 읽기 트랜잭션에서 조회한다.
- `POST /search/reports/{reportId}/feedback`은 본인의 종합점수가 계산된 리포트에 의견 1건을 저장한다. 점수와 세부 점수는 서버가 읽은 저장 스냅샷의 제출 시점 값이다. 종합점수 미산출은 R004/409, 중복 의견은 C002/409이며 유일 제약으로 동시 제출을 처리한다.
- 두 조회와 의견 저장은 외부 API나 사용량 원장에 접근하지 않는다. V7은 최근 기록 정렬 인덱스와 의견 테이블을 추가한다. 기존 V6 리포트의 저장 내용은 조회 시 해석한다.

### 리포트 추가 입력과 재계산

- JWT `PATCH /search/reports/{reportId}/inputs`는 본인의 완료 리포트에서 예상 판매가, 대분류 코드, 키워드, 옵션, CPC를 변경한다. 최소 한 필드가 필요하며 생략한 값은 유지한다. CPC 0은 입력값이고 명시적 null은 초기화다. 알려지지 않은 카테고리·상품에 속하지 않은 옵션·직접 입력 리포트의 옵션은 400이다.
- 판매가·카테고리 변경은 저장된 원가·시장 데이터와 쿠팡 요율로 기준 마진·수익성·공급 조건·종합점수와 관련 수치의 초기값을 갱신한다. 부족한 시장가나 요율은 계산 대기 및 필요 입력으로 표시한다. CPC만 변경하면 점수를 유지하고 기획서 6.4의 등급 조정 규칙을 적용한다. 30%와 50%는 초과 조건이며, 원화 마진 비교에는 소수점 원래 값을 사용한다.
- 키워드·옵션 변경은 영향받는 시장 지표나 공급 조건을 `ReportInputDataProvider`로 갱신한다. 옵션은 기존 리포트의 소싱처·상품번호로 `UrlProductCatalog`에서 검증하고 해당 도매가·재고를 사용한다. 외부 조회를 할 수 없으면 R006/503을 반환하고 기존 리포트를 그대로 유지한다.
- 조회 결과를 바탕으로 계산한 드래프트는 완료 당시 리포트 ID에 저장한다. 외부 조회 동안 DB 잠금을 유지하지 않으며, 최종 쓰기는 회원 인증 버전과 읽었던 JSON 스냅샷을 다시 확인한다. 저장 사이에 다른 요청이 수정했으면 R005/409다. 실제 값이 같으면 최신 저장 시각을 유지한다. 리포트 내 변경은 무료 정밀분석 사용량을 차감하지 않는다.

## 관심상품과 알림

- DB 프로필에서 `/favorites` 등록·목록, `/favorites/{favoriteId}` 상세·삭제, `.../status` 상태 변경, `.../sales-results` 판매 결과 기록을 제공한다. 등록은 본인의 완료된 직접 입력 리포트에서 키워드·공급가·배송비·마진·점수를 복사한다. URL 리포트 등록은 공급처 정보 모델 연동 전까지 C001/400이다. 중복 기준은 회원과 원본 리포트 ID다. 최초 등록은 201, 같은 상품의 재등록은 200으로 최초 등록값을 유지하고 요청한 상태를 적용한다.
- 무료 한도 10개는 두 상태의 합계이며 대시보드와 같은 집계를 사용한다. 회원 행을 잠근 트랜잭션과 활성 리포트 유일 인덱스로 경합을 처리한다. 한도 초과는 F001/409, 없는 ID와 타인의 ID는 C003/404다. 같은 상태 요청은 상태 변경 시각을 유지한다. 목록·상세의 `updatedAt`은 추적 데이터 시각이다.
- 목록은 `page=0`, 알림 목록은 `page=1`부터 시작하고 둘 다 `size=20`, 최대 100을 사용한다. 직접 입력 관심상품은 마지막 저장값을 기준으로 조회하며 공급사·공급가 이력·품절 이력·옵션 정보는 null이다. 시장가 갱신 상태는 `UNAVAILABLE`이고 저장된 종합점수가 있으면 점수 변화는 0이다. 조회·등록·판매 결과 기록은 무료 리포트 사용량을 차감하지 않는다.
- V8은 관심상품 행에 등록값을 추가한다. V5에서 리포트 연결 없이 만들어진 행은 마이페이지 사용량과 한도 집계에 보존하며 목록과 상세는 상품 정보가 있는 등록만 반환한다. 삭제는 활성 목록에서 제거하고 한도를 반환한다. 기존 판매 결과는 등록 행과 함께 보존하며, 새 등록에는 새 ID를 발급한다. 판매 결과 POST는 매번 기록을 추가하고 상세는 최신 기록 한 건을 반환한다. 장기 보존·삭제는 개인정보 정책 확정 시 결정한다.
- `/my/notification-settings`는 설정이 없는 회원에게 명세 기본값을 제공한다. PATCH는 값이 포함된 필드만 변경하고, 명시적 null·잘못된 JSON 타입은 C009/400, 값이 없는 패치와 허용 범위 밖 기준값은 C001/400을 반환한다. 회원 행 잠금으로 동시에 수신된 부분 변경이 서로 덮어쓰는 것을 방지한다.
- `/home/notifications`는 읽지 않은 알림을 먼저 표시하며 그룹별 최신순과 ID 역순으로 정렬한다. `unreadCount`는 필터와 무관한 전체 미확인 건수다. 읽음 처리는 회원 범위에서 수행하며 최초 읽음 시각을 유지한다. V8은 설정과 알림·읽음 상태를 저장하고 외부 이벤트 감지와 알림 전송은 연동 단계에서 구현한다.

## 키워드 스크리닝과 쇼핑 반응

- `POST /search/screenings`는 키워드 앞뒤 공백을 제거하고 최초 등장 순서대로 중복을 제거한 뒤 처음 10개를 처리한다. `excludedCount`는 처리 범위를 넘은 고유 키워드 수다. 빈 키워드와 500자를 초과하는 키워드는 C001/400, 문자열이 아닌 값은 C009/400이다.
- 회원 요청은 V5 실행 행과 V9 결과를 한 트랜잭션에서 저장한다. `GET /search/screenings/recent`는 본인의 최신 성공 실행에서 점수 상위 4개를 반환한다. 동점은 입력 순서, 실행 시각 동률은 나중에 저장한 실행을 우선한다. 리포트 사용량은 차감하지 않는다.
- 비회원의 화면 최근 기록은 브라우저에서 관리한다. 서버는 쇼핑 반응의 선행 결과를 확인하기 위해 무작위 ID의 스냅샷을 24시간 조회 가능하게 보관하고 다음 비회원 저장 때 만료 행을 정리한다. 회원 결과를 사용하려면 같은 회원의 JWT가 필요하다.
- `GET /search/categories`는 명세의 공개 분야 코드 11개를 반환한다. `POST /search/screenings/{screeningId}/shopping-analysis`는 같은 스크리닝에 저장된 키워드와 분야를 검증하고, 원래 검색 데이터 기준일과 지표를 재사용해 해당 카드 점수를 계산한다. 미등록 분야는 C001/400, 없는 결과·키워드·만료된 비회원 결과·다른 회원 결과는 C003/404다.
- 기획서 4.4의 가안 배점은 수요 40, 성장 25, 경쟁 25, 쇼핑 반응 10이다. 지표가 빠진 항목은 분모에서 빼고 정수 점수로 반올림한다. 증감률은 KST의 마지막 완료일을 기준으로 최근 28일과 전년 동일 종료일 기준 28일의 평균을 비교한다. 한 구간이 불완전하거나 전년 평균이 0이면 증감률을 null로 반환한다. 월별 추세는 기준일의 달을 포함한 12개 달의 일별 지수 평균이다. 양쪽 증감률이 있을 때만 각각 +10% 이상인지 판정해 4분면을 계산한다.
- `ScreeningDataProvider`는 검색광고와 향후 데이터랩 조회를 연결하는 경계다. 실제 제공자 부재나 필수 검색광고 지표 누락은 S001/503이다. 외부 호출 묶음·데이터랩 예산·IP당 요청 제한은 실제 데이터 연결 단계에서 적용한다.
