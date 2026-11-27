# Chozy 백엔드 구조와 개발 규칙

## 문서와 설정의 역할

실행·IntelliJ·환경변수·빌드 방법은 [README](../README.md)를 따른다. 이 문서는 아키텍처와 개발 규칙을 관리한다. 현재 진척과 다음 작업은 [CONTEXT](CONTEXT.md)에 기록한다. 작업별 결과는 Git에서 제외한 `temp.md`에 최신 내용으로 덮어쓴다.

설정은 `application.yml`을 공통값으로, `application-{profile}.yml`을 환경 차이로 관리한다. 비밀값은 환경변수로 주입하며 `.env` 자동 로드를 가정하지 않는다. 기본/일반 테스트 환경은 DB 없이 실행한다. DB 접속은 격리 개발용 `dbdev` 또는 별도 검토한 `database/prod`에서 활성화한다.

DB 자동 초기화는 [환경과 migration 경계](#환경과-migration-경계), 인증과 CORS는 [보안 계약](#응답예외보안)을 따른다. Actuator는 미도입이다.

기능별 계약은 [계정·사용량](ACCOUNTS.md), [분석·리포트](ANALYSIS.md), [관심상품·알림](FAVORITES_NOTIFICATIONS.md)에 있다. 호출 횟수와 공급사 제한은 [API_USAGE](API_USAGE.md)를 따른다.

## 패키지와 의존 방향

단일 애플리케이션의 기능별 모듈형 모놀리스다. 기능별 패키지와 현재 통합 경계는 다음과 같다.

| `com.chozy.server` 하위 | 책임 |
| --- | --- |
| auth | 가입·로그인·토큰 수명·본인인증 |
| home | 공급 추천·쿠팡 베스트·네이버 시즌과 비교, `home.repository`의 완료 발행본 JDBC 조회 |
| screening | 키워드 스크리닝·쇼핑 반응·최근 기록 |
| analysis | URL 판별·실시간 정밀분석 요청과 진행 상태 |
| report | 리포트 조회·수정·의견. `ReportRecalculator`는 스냅샷·가격·시뮬레이션을, `ReportScoring`은 점수·등급 정책을 담당 |
| favorite | 관심상품·판매 상태·판매 결과 |
| notification | 사용자 알림·읽음 상태·설정 |
| member | 프로필·비밀번호 변경·동의·탈퇴 |
| usage | 월 사용량과 한도, 홈/마이페이지 공통 값 |
| integration.external | 사용자 요청 시 네이버·쿠팡 등 호출과 공동 예산 연계 |
| common.response/error | HTTP envelope·예외 및 코드 |
| common.security/config | 접근 정책·JWT 필터·CORS·OpenAPI |

`기능.controller → 기능.service → 기능.repository` 방향을 따른다. Controller는 HTTP/검증/DTO 변환, Service는 유스케이스·트랜잭션·사용자 권한, Repository는 저장/조회만 담당한다. 자체 데이터에는 JPA, Collector 발행 조회에는 JDBC를 우선 사용한다. 각 기능에 필요한 경우에만 dto/entity 등의 하위 패키지를 만든다. 전역 controller/service/repository, BaseService, 형식적 인터페이스는 추가하지 않는다.

DTO는 API 입력·출력 계약이며 Entity를 그대로 반환하거나 요청 DTO로 바인딩하지 않는다. 다른 도메인의 Repository/Entity에 직접 의존하기보다 해당 도메인의 필요한 Service 기능을 통해 접근한다. 외부 HTTP/SQL 형식을 업무 Service에 흩뿌리지 않는다. common에는 업무 점수·사용량·관심상품 규칙을 넣지 않는다.

## 응답·예외·보안

CORS는 `CORS_ALLOWED_ORIGINS`에 정확한 HTTP(S) origin을 쉼표로 설정한다. 기본은 빈 목록이며 wildcard·경로·사용자정보는 거부한다. Bearer 인증은 stateless로 운영하며 credentials는 false이고 CSRF·form login·basic login·session·request cache는 사용하지 않는다. 쿠키 인증 도입 시 CSRF·CORS 정책을 다시 검토한다. SQL과 바인딩 로그를 제한하고 요청 본문·Authorization·예외 원문을 기록하지 않는다.

성공은 `ApiResponse<T>(status,message,data)`로 반환한다. 생성 API는 HTTP 201과 envelope.status=201을 함께 지정한다. 삭제/로그아웃처럼 데이터가 없는 명세는 data=null을 유지한다. 전역 성공 응답 자동 wrapping은 하지 않는다.

오류는 기존 명세 예시의 `message/status/errors/code`를 사용한다. C001~C012, A003~A006은 원문 표의 상태/메시지와 같다. Bean Validation C001, 타입 C005, 누락 파라미터 C008, JSON C009, 메서드 C007, 미디어 C010, 경로 부재 C003, 업무 충돌 C002, 권한 C006, 예상 밖 오류 C004를 공통 처리한다. C011 업로드/C012 비동기 기반 매핑도 제공한다. 오류 응답에 내부 메시지·SQL·stack trace·입력값을 넣지 않는다. 예기치 않은 예외 로그는 타입만 남긴다.

공개 업무 경로는 HTTP 메서드와 정확한 경로로 허용한다. 나머지 업무 경로는 인증이 필요하다. `common.security.JwtAuthenticationFilter`는 Bearer Access Token의 서명과 만료를 확인한 뒤 회원의 현재 자격 증명 버전을 검증한다. `@AuthenticationPrincipal AuthenticatedMember`에서 회원 ID를 얻고 Service에서 자원 소유권을 검증한다. 잘못된 Authorization은 공개 API에서도 오류를 반환한다. 회원 인증이 필요한 경로는 OpenAPI에 bearerAuth를 표시한다. offline/dev는 DB 의존 업무 빈 없이 기동한다.

OpenAPI 3.1은 MVC 매핑과 실제 DTO에서 요청·성공 스키마를 생성한다. `@OpenApiContract`는 공통 오류와 API별 오류를 문서에 추가하고 기존 `ErrorCode`에서 예시를 생성한다. `dbdev`는 업무 API 문서, `dev`는 DB 없는 공통 문서를 공개한다. `offline/database/prod`는 기본 비활성이다. Swagger UI 인증은 현재 페이지에서만 유지한다. 접속·프론트엔드 설정은 [API 테스트](API_TESTING.md#swagger에서-개발-api-테스트)를 따른다.

API를 구현할 때 **HTTP 메서드 + 정확한 경로**를 SecurityConfiguration에 등록한다. `/auth/**`, `/home/**`, `/search/**` 전체 공개는 금지한다. 현재 명세 분류:

- 공개: POST `/auth/signup`, `/auth/login`, `/auth/reissue`; GET `/auth/check-login-id`.
- 공개 조회: GET `/home/products/recommended`, `/home/keywords/naver`, `/home/keywords/coupang`, `/home/keywords/compare`, `/search/categories`, `/search/coupang-categories`.
- 공개 처리: POST `/search/products/resolve-url`, `/search/screenings/{screeningId}/shopping-analysis`.
- POST `/search/screenings`는 비회원에게 공개하며 JWT 전달 시 회원 기록을 저장한다. 선택 인증 경로에서도 유효하지 않은 JWT는 A004/401, 만료된 토큰은 A003/401을 반환한다.
- 인증: 로그아웃, `/home/report-usage`, 알림, 최근 기록, 분석 요청/상태, 리포트, 관심상품, 마이페이지. prefix 전체 공개에 섞지 않는다.

## Collector와 PostgreSQL 경계

Collector의 `products/product_options/product_history/product_events`는 최신 상태/옵션/실제 변경 관측/출처 있는 사건이며 Spring 소유가 아니다. 점수 큐·산식·수집·완료 증표·발행은 Collector 책임이다. Spring에서 점수 산식을 복사하거나 홈 점수를 재계산하지 않는다. NULL/미확인/0을 구분하고 DOMEME/OWNERCLAN을 합쳐 순위 매기지 않는다.

공급 추천은 검토된 스키마의 SQL `public.read_supply_home(source,size)`로 읽는다(size 1~1000, source DOMEME/OWNERCLAN). 발행본의 `updatedAt`과 순위순 `items`를 응답에 사용한다. 완료본 없음(SQL NULL)과 완료본의 빈 items를 구분한다. 접속 또는 응답 해석 실패는 H001/503이다.

쿠팡과 네이버 시즌 JDBC 조회는 각각 Collector의 `coupang_API/persistence/best.py:read_published`, `naver_API/queries.py:read_published/read_compare`의 발행 계약을 따른다. API마다 SQL 한 문장으로 최신 완료 배치를 고르므로 메타데이터와 상품·키워드를 같은 DB 스냅샷에서 읽는다. 비교는 같은 기준일의 완료 쿠팡 발행본에서 상품명과 키워드의 공백을 제거한 뒤 포함 여부를 확인하고, 카테고리를 가로질러 상품 ID를 중복 제거해 센다. 네이버 단독 조회는 쿠팡 객체에 의존하지 않는다.

홈 시장 조회 전용 Collector 로그인에는 `public` 스키마 `USAGE`와 아래 조회 권한이 필요하다. 테이블 이름은 Collector 운영 스키마의 `public` 기준이다. 연결에는 읽기 전용 기본 트랜잭션과 제한 시간을 설정한다. 운영 로그인과 권한 적용은 별도 배포 작업이다.

| 객체 | 필요한 SELECT 컬럼 |
| --- | --- |
| `coupang_best_batches` | `snapshot_date, published_at, status` |
| `coupang_best_categories` | `snapshot_date, category_id, category_name, status` |
| `coupang_best_published` | `snapshot_date, category_id, rank, product_id, product_name, price, is_rocket, previous_rank, rank_change, rank_status` |
| `naver_season_batches` | `snapshot_date, published_at, status` |
| `naver_season_themes` | `snapshot_date, theme_code, theme_name` |
| `naver_season_published` | `snapshot_date, theme_code, rank, keyword, monthly_search_count, growth_rate` |

Spring 사용자/인증/리포트/관심상품/알림/사용량 데이터는 `chozy` 소유다. `db/migration/chozy`와 `chozy.flyway_schema_history`는 Collector Python migration/ledger와 독립적이다. 운영에서는 전용 migration 관리자와 비소유 Spring runtime 로그인을 구분하며 runtime에는 DDL 권한을 주지 않는다. 운영 Flyway 적용은 별도 검증·승인 작업이며 애플리케이션 startup에서는 disabled다. 필요 시 자체 저장과 Collector 조회에 별도 DataSource/계정을 둔다. Collector 관리자 계정을 재사용하지 않는다.

Collector 운영 DB의 함수 실행 권한은 `access.py`의 SECURITY DEFINER 및 고정 `search_path` 권한 계획을 따른다. Spring은 별도 비소유 로그인으로 예약·차단 함수를 실행하고 예산 테이블을 직접 수정할 권한을 갖지 않는다. 적용 및 연결 상태는 [CONTEXT](CONTEXT.md#개발-순서)를 따른다.

실시간 요청 호출은 정기 발행 조회와 별개다. 실제 계정/API별 공동 `reserve_api_call`/차단 계약과 권한을 먼저 확인한다. 로컬 카운터로 대체하거나 usage 초기화/차단 해제로 우회하지 않는다. 주소·인증·요청/응답 계약이 미정인 Collector HTTP/AI 클라이언트와 외부 설정 bean은 미리 만들지 않았다. 계약 확정 시 `@ConfigurationProperties`+검증, 환경변수 비밀값 주입, timeout/오류 매핑을 통합 경계에 추가한다.

## 홈 공급 조건 우수 상품 조회

- 공개 `GET /home/products/recommended`는 필수 `source`(`DOMEME`, `OWNERCLAN`)와 선택 `size`(기본 10, 1~1000)를 받는다. 필수값 누락은 C008/400, 타입과 소싱처 코드 오류는 C005/400, 크기 범위 오류는 C001/400이다.
- `RecommendedProductReader`는 한 소싱처의 최신 완료 발행본에서 요청한 개수까지 순위순으로 읽는다. HTTP 응답은 발행본의 `updatedAt`을 반환하고 순위, 미확인 지표의 null, 실제 측정값 0을 유지한다. `shippingDays`는 소수를 포함할 수 있는 숫자 또는 null이다. 조회 결과에 발행본이 없으면 `updatedAt: null, items: []`를, 상품이 없는 완료 발행본은 `items: []`와 해당 발행본 시각을 반환한다. 두 경우 모두 200이다. [발행본 검증 절차](API_TESTING.md#홈-공급-추천-발행본-검증)
- 발행본 조회 제공자가 미등록이거나 데이터 소스를 사용할 수 없으면 H001/503을 반환한다. 추천 JDBC 설정은 홈 시장 발행본 JDBC와 독립적으로 활성화한다.

## 홈 쿠팡 베스트 상품 조회

- 공개 `GET /home/keywords/coupang`은 선택값 `categoryId`를 양의 정수로 받는다. 범위 오류는 C001/400이고 형식 오류는 C005/400이다. 날짜는 원본 명세에 따라 요청값으로 받지 않으며, 스냅샷 날짜 기준 최신 완료 발행본의 발행 시각을 `updatedAt`으로 반환한다.
- `CoupangBestReader`는 카테고리와 순위순 상품을 동일 완료 발행본에서 읽는다. 카테고리는 ID 오름차순으로 반환하고 미선택 시 첫 카테고리를 기본으로 선택한다. 요청한 ID가 그 발행본에 없으면 요청 ID와 발행 시각을 유지하고 상품만 빈 배열로 반환한다. 완료 발행본이 없으면 두 목록을 빈 배열로, `updatedAt`과 `selectedCategoryId`를 null로 반환한다.
- 순위 변동은 발행본 값을 유지한다. 전일 비교 기준이 없으면 `previousRank`, `rankChange`, `rankStatus`는 모두 null이다. 전일 발행본은 있지만 해당 상품이 전일 순위에 없으면 `rankStatus`는 `NEW`다. 조회 제공자가 미등록이거나 데이터 소스를 사용할 수 없으면 H002/503을 반환한다.

## 홈 네이버 시즌 키워드와 쿠팡 비교

- 공개 `GET /home/keywords/naver`와 `GET /home/keywords/compare`는 선택값 `themeCode`를 양의 정수로 받는다. 최신 완료 네이버 발행본을 기준으로 코드가 가장 작은 테마를 기본 선택하고 순위순 최대 10개를 사용한다. 요청한 테마가 발행본에 없으면 요청 코드와 빈 항목을 반환한다. 발행본 자체가 없으면 갱신 시각과 선택 코드는 null이다.
- 비교는 같은 기준일이며 상품이 있는 완료 쿠팡 발행본에서 키워드별 고유 일치 상품 수를 사용한다. 1개 이상과 0개를 각각 `matchedKeywords`와 `unmatchedKeywords`로 반환하고 두 발행 시각 중 나중 값을 사용한다. 비교할 쿠팡 발행본이 없으면 두 목록을 비우고 안내를 반환한다. 불완전한 일치 건수와 미등록 조회 제공자는 H003/503이다.

## Spring DB 기반과 회원 정책

### 환경과 Migration 경계

기본 `ddl-auto=none`, DB 프로필은 `validate`, SQL init은 `never`다. 기동 시 Flyway는 `dbdev`에서만 허용하며 `database/prod`에서 활성화를 시도하면 DB 연결 전에 거부한다. 배포에서는 Collector 조회와 공동 예산 접속을 각각 설정한다. 컨테이너는 검토한 external network를 쓰고 호스트 실행은 검토한 공개 포트로 접속한다.

`compose.dev.yml`의 프로젝트는 `chozy-spring-dev`, 컨테이너는 `chozy-spring-dev-postgres-1`, volume은 `chozy-spring-dev_spring_dev_data`, network는 `chozy-spring-dev_spring_dev`다. PostgreSQL 16을 `127.0.0.1:55432`에만 공개한다. Collector의 5432·`product-data-collector_postgres_data`·`product-data-collector_default`와 연결하거나 공유하지 않는다. stop/down 시 volume은 보존하며 `down -v`는 사용하지 않는다. 이 개발용 관리자 계정은 격리 개발에 한정하며 운영 권한 모델로 복제하지 않는다.

실행·연결·검증 명령은 [README](../README.md#격리-개발-db-실행)에 둔다. 개발 연결 정보는 `CHOZY_DEV_DB_NAME`, `CHOZY_DEV_DB_USERNAME`, `CHOZY_DEV_DB_URL`, `CHOZY_DEV_DB_PASSWORD` 환경변수로 관리한다. DB명·사용자·로컬 주소에는 안전한 기본값이 있으며 안전 검증은 문서의 canonical 대상만 허용한다. 비밀번호 기본값은 없다. 운영 접속 환경변수 `CHOZY_DB_*`와 분리한다. `dbdev`의 datasource URL/사용자가 달라지거나 offline/dev/prod/database와 혼용하면 bean 생성 전에 실패한다. Flyway 별도 URL/계정 주입도 거부한다. 접속 실패는 Hikari 제한 시간과 초기 JPA/Flyway 검증에서 기동 실패로 처리하며 offline으로 자동 전환하지 않는다.

Flyway core와 PostgreSQL 모듈은 Boot BOM 버전을 사용한다. `V1__create_chozy_schema.sql`은 스키마 경계, `V2__create_members.sql`은 회원 테이블을 담당한다. Flyway가 history 배치를 위해 스키마를 먼저 만들므로 V1은 `IF NOT EXISTS`다. 이력은 `chozy.flyway_schema_history`에 checksum·버전·성공 여부로 관리한다. validate-on-migrate=true, baseline-on-migrate=false, clean-disabled=true다. 스키마 변경은 다음 버전의 migration으로 추가한다. Collector `schema_migrations`와 기존 DDL은 참조하거나 수정하지 않는다. 운영에서도 스키마·소유자·runtime 권한을 분리한다.

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
5. 이메일 인증과 비밀번호 찾기·재설정은 후속이다. 기획서의 이메일은 알림용 필수 입력이며, 인증을 도입하면 목적과 계약을 정한다.

## 자료 차이와 미확정 사항

- 서버 참고 자료는 PDF 1개와 Markdown 41개(실제 URL이 있는 API 39개, 템플릿 2개)다. 공통/인증 에러 표는 서버 참고 폴더에 없어 Collector `docs/references/API 명세서/에러 코드` 원본을 읽었다. 원본 자료는 수정하지 않았다.
- 오류 envelope는 빈 템플릿의 A007 예시에만 명시되어 있다. errors 항목의 세부 필드/값은 정의되지 않아 빈 배열로 유지한다. 새 traceId/timestamp/path 필드를 만들지 않았다. A007은 코드 표에 없어 등록하지 않았다.
- 인증·토큰 오류는 [인증 오류 계약](ACCOUNTS.md#오류-계약-보완)을 따른다. 406 등 표에 없는 HTTP 오류 정책은 미확정이며 미분류 MVC 오류는 C004로 처리한다.
- 본인인증·DI·소셜 연결의 명세 차이는 [미구현 인증과 원본 차이](#미구현-인증과-원본-차이)에 있다. 수집·호출 정책의 최신 상태는 [Collector 문서](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/CONTEXT.md)를 참고한다.

## 코드와 테스트 배치

Java는 UTF-8, 공백 2칸을 사용하며 Maven XML은 공백 4칸을 사용한다. `.editorconfig`를 기준으로 IDE 설정을 맞추고, `.gitattributes`로 Windows 실행 스크립트는 CRLF, 소스와 Unix Wrapper는 LF를 유지한다. 참고 원본은 포맷 대상으로 삼지 않는다.

테스트 패키지는 검증하는 운영 코드의 패키지를 따른다. 애플리케이션 기동 검증은 루트의 `ChozyServerApplicationTest`, 공통 응답/예외/보안 검증은 `common`의 각 책임 아래에 둔다. 여러 책임을 하나의 FoundationTest에 계속 쌓지 않는다. 테스트 fixture는 `support`에 두고 `@TestComponent`로 일반 스캔에서 제외한 뒤 필요한 테스트에서만 명시적으로 가져온다. 업무 API가 추가되면 해당 기능의 같은 패키지에 테스트를 추가한다.

## 검증과 다음 API 개발

다음 구현 범위는 [서버 개발 순서](CONTEXT.md#개발-순서)를 따른다.

[README의 검증 명령](../README.md#검증)을 사용한다. 기본 회귀는 DB 없이 공통 응답·MVC 오류·비밀값 비노출·인증·CORS·OpenAPI·DB 설정 경계를 검증한다. `db-test`는 Testcontainers의 전용 PostgreSQL 16에서 Flyway와 JPA validate를 실행하고 동시 요청·Unique·정규화 CHECK·nullable DI·실패 롤백과 재연결을 검증한다. 운영·개발 DB URL은 입력으로 받지 않으며 Docker가 없으면 실패한다. 테스트 전용 Controller는 `src/test`에 둔다.

다음 API는 원문 method/path/request/response/JWT부터 확인하고 해당 기능의 Controller·DTO·Service·필요한 Repository만 추가한다. 인증 규칙과 소유권, 실제 HTTP 상태/envelope 일치, 정상/검증/인증/권한 오류 테스트를 함께 작성한다. 자체 데이터 변경은 전용 migration으로 리뷰한다. 완료 발행 조회와 실시간 호출은 별도 통합 책임으로 유지한다. 구현하지 않은 API를 문서나 코드에 엔드포인트로 등록하지 않는다.

버전 근거: [Spring Boot 3.5 공식 문서](https://docs.spring.io/spring-boot/3.5/), [springdoc 호환성 안내](https://springdoc.org/v2/), Maven Central의 실제 배포 artifact 확인. Boot 3.x 요구를 유지했으며 배포 전 해당 계열 지원 정책과 보안 패치를 재검토한다.
