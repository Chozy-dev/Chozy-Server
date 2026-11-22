# Chozy 백엔드 구조와 개발 규칙

## 문서와 설정의 역할

실행·IntelliJ·환경변수·빌드 방법은 [README](../README.md)를 따른다. 이 문서는 아키텍처와 개발 규칙을 관리한다. 작업별 결과는 Git에서 제외한 `temp.md`에 기록한다. 작성할 때마다 이전 내용을 전부 지우고 최신 작업 결과로 파일 전체를 덮어쓴다. 이어 쓰기와 결과 이력 누적은 하지 않는다.

설정은 `application.yml`을 공통값으로, `application-{profile}.yml`을 환경 차이로 관리한다. 비밀값은 환경변수로 주입하며 `.env` 자동 로드를 가정하지 않는다. 기본/일반 테스트 환경은 DB 없이 실행한다. DB 접속은 격리 개발용 `dbdev` 또는 별도 검토한 `database/prod`에서 활성화한다.

기본 `ddl-auto=none`, DB 프로필은 `validate`, SQL init은 never다. Hibernate는 테이블을 생성하지 않는다. Flyway 의존성은 도입했으며 `dbdev`에서만 startup migration을 허용한다. `database/prod`는 disabled이고 외부 설정으로 켜도 연결 전 안전 검증에서 거부한다. Actuator·JWT 라이브러리는 실제 구현 단계에 추가한다.

CORS는 `CORS_ALLOWED_ORIGINS`에 정확한 HTTP(S) origin을 쉼표로 설정한다. 기본은 빈 목록, wildcard·경로·사용자정보는 거부한다. Bearer 헤더 방식의 Stateless 구성을 전제로 credentials는 false이고 CSRF/form login/basic login/session/request cache를 사용하지 않는다. 쿠키 인증을 도입하면 CSRF·CORS 정책을 다시 검토한다. SQL/바인딩 로그는 제한하고 요청 본문·Authorization·예외 원문을 로깅하지 않는다.

## 패키지와 의존 방향

단일 애플리케이션의 기능별 모듈형 모놀리스다. 아래 기능 패키지는 **설계 경계**이며 API 구현 시 필요한 파일부터 만든다.

| `com.chozy.server` 하위 | 책임 |
| --- | --- |
| auth | 가입·로그인·토큰 수명·본인인증 |
| home | 완료 추천·인기 키워드 응답 조합 |
| screening | 키워드 스크리닝·쇼핑 반응·최근 기록 |
| analysis | URL 판별·실시간 정밀분석 요청과 진행 상태 |
| report | 저장 리포트·입력 보완·재계산·의견 |
| favorite | 관심상품·판매 상태·판매 결과 |
| notification | 사용자 알림·읽음 상태·설정 |
| member | 프로필·비밀번호 변경·동의·탈퇴 |
| usage | 월 사용량과 한도, 홈/마이페이지 공통 값 |
| integration.collector | 완료 발행 조회 JDBC/SQL과 외부 데이터 변환 |
| integration.external | 사용자 요청 시 네이버·쿠팡 등 호출과 공동 예산 연계 |
| integration.ai | 계약 확정 후 필요한 AI 연동 |
| common.response/error | HTTP envelope·예외 및 코드 |
| common.security/config | 접근 정책·향후 JWT 필터·CORS·OpenAPI |

`기능.controller → 기능.service → 기능.repository` 방향을 따른다. Controller는 HTTP/검증/DTO 변환, Service는 유스케이스·트랜잭션·사용자 권한, Repository는 저장/조회만 담당한다. 자체 데이터에는 JPA, Collector 발행 조회에는 JDBC를 우선 사용한다. 각 기능에 필요한 경우에만 dto/entity 등의 하위 패키지를 만든다. 전역 controller/service/repository, BaseService, 형식적 인터페이스는 추가하지 않는다.

DTO는 API 입력·출력 계약이며 Entity를 그대로 반환하거나 요청 DTO로 바인딩하지 않는다. 다른 도메인의 Repository/Entity에 직접 의존하기보다 해당 도메인의 필요한 Service 기능을 통해 접근한다. 외부 HTTP/SQL 형식을 업무 Service에 흩뿌리지 않는다. common에는 업무 점수·사용량·관심상품 규칙을 넣지 않는다.

## 응답·예외·보안

성공은 `ApiResponse<T>(status,message,data)`로 반환한다. 생성 API는 HTTP 201과 envelope.status=201을 함께 지정한다. 삭제/로그아웃처럼 데이터가 없는 명세는 data=null을 유지한다. 전역 성공 응답 자동 wrapping은 하지 않는다.

오류는 기존 명세 예시의 `message/status/errors/code`를 사용한다. C001~C012, A003~A006은 원문 표의 상태/메시지와 같다. Bean Validation C001, 타입 C005, 누락 파라미터 C008, JSON C009, 메서드 C007, 미디어 C010, 경로 부재 C003, 업무 충돌 C002, 권한 C006, 예상 밖 오류 C004를 공통 처리한다. C011 업로드/C012 비동기 기반 매핑도 제공한다. 오류 응답에 내부 메시지·SQL·stack trace·입력값을 넣지 않는다. 예기치 않은 예외 로그는 타입만 남긴다.

아직 업무 API 공개 규칙은 등록하지 않았다. 기본은 모든 요청 인증 필수이며 JWT 발급/검증과 임시 인증 성공은 없다. JWT를 구현할 때 `common.security`에 검증 필터/인증 주체를 추가하고, 검증된 인증 결과만 SecurityContext에 저장한다. 업무 Service에서도 리포트/관심상품 소유권을 검증해야 한다. OpenAPI bearerAuth 정의는 문서 표기일 뿐 실제 인증 기능이 아니다. 인증 필수 API에만 `@SecurityRequirement(name="bearerAuth")`를 추가한다.

API를 구현할 때 **HTTP 메서드 + 정확한 경로**를 SecurityConfiguration에 등록한다. `/auth/**`, `/home/**`, `/search/**` 전체 공개는 금지한다. 현재 명세 분류:

- 공개: POST `/auth/signup`, `/auth/login`, `/auth/reissue`; GET `/auth/check-login-id`.
- 공개 조회: GET `/home/products/recommended`, `/home/keywords/naver`, `/home/keywords/coupang`, `/home/keywords/compare`, `/search/categories`, `/search/coupang-categories`.
- 공개 처리: POST `/search/products/resolve-url`, `/search/screenings/{screeningId}/shopping-analysis`.
- POST `/search/screenings`는 비회원 허용, JWT 선택 전달 시 회원 기록 저장. JWT 구현 시 잘못된 토큰을 익명으로 조용히 무시하지 않도록 정책 확정.
- 인증: 로그아웃, `/home/report-usage`, 알림, 최근 기록, 분석 요청/상태, 리포트, 관심상품, 마이페이지. prefix 전체 공개에 섞지 않는다.

## Collector와 PostgreSQL 경계

Collector의 `products/product_options/product_history/product_events`는 최신 상태/옵션/실제 변경 관측/출처 있는 사건이며 Spring 소유가 아니다. 점수 큐·산식·수집·완료 증표·발행은 Collector 책임이다. Spring에서 점수 산식을 복사하거나 홈 점수를 재계산하지 않는다. NULL/미확인/0을 구분하고 DOMEME/OWNERCLAN을 합쳐 순위 매기지 않는다.

완료 추천은 검토된 스키마의 SQL `read_supply_home(source,size)`를 우선 사용한다(size 1~1000, source DOMEME/OWNERCLAN). JSONB의 완료 batchId/asOf/formulaVersion/items를 보존하고 products 현재값과 재결합하지 않는다. 완료본 없음(SQL NULL)과 완료본의 빈 items를 구분한다.

쿠팡은 `coupang_API/persistence/best.py:read_published`, 네이버는 `naver_API/queries.py:read_published/read_compare`가 실제 조회 계약이다. Python 함수는 Spring에서 직접 호출할 HTTP 서버가 아니다. 향후 해당 SQL 의미를 JDBC로 구현하고 필요한 읽기 권한을 별도 확인한다. 한 SQL로 최신 완료 배치 전체를 고르고 선택 카테고리/테마가 없다고 과거 데이터를 섞지 않는다. 비교는 네이버와 같은 날짜의 완료 쿠팡만 사용한다. 현재 쿠팡/네이버 Spring runtime 권한은 확정되지 않았다.

Spring 사용자/인증/리포트/관심상품/알림/사용량 데이터는 `chozy` 소유다. 현재는 최소 `members`만 구현했다. `db/migration/chozy`와 `chozy.flyway_schema_history`는 Collector Python migration/ledger와 독립적이다. 운영에서는 전용 migration 관리자와 비소유 Spring runtime 로그인을 구분하며 runtime에는 DDL 권한을 주지 않는다. 운영 Flyway 적용은 별도 검증·승인 작업이며 애플리케이션 startup에서는 disabled다. 필요 시 자체 저장과 Collector 조회에 별도 DataSource/계정을 둔다. Collector 관리자 계정을 재사용하지 않는다.

**2026-11-22 문서 기준** 운영에는 stage7_supply_scoring, stage8_operations, stage7_history_policy_v2, stage10_scoring_performance 네 migration이 미적용이다. runtime_budget_guard·runtime 권한 분리·실제 Spring 로그인/DSN 전환도 미완료다. 사본 권한 검증은 운영 적용 완료가 아니다. read_supply_home/reserve_api_call의 원본은 INVOKER이고 access.py의 DEFINER/고정 search_path 권한 계획은 운영 미적용이다. EXECUTE만 부여하면 충분하다고 가정하거나 관리자 계정으로 우회하지 않는다. 1,500만 성능·용량과 지속 수집 SLA도 미검증이다.

실시간 요청 호출은 정기 발행 조회와 별개다. 실제 계정/API별 공동 `reserve_api_call`/차단 계약과 권한을 먼저 확인한다. 로컬 카운터로 대체하거나 usage 초기화/차단 해제로 우회하지 않는다. 주소·인증·요청/응답 계약이 미정인 Collector HTTP/AI 클라이언트와 외부 설정 bean은 미리 만들지 않았다. 계약 확정 시 `@ConfigurationProperties`+검증, 환경변수 비밀값 주입, timeout/오류 매핑을 통합 경계에 추가한다.

## Docker 연결 계획

기존 Collector Compose는 postgres:16, 컨테이너 product-data-collector-postgres, 호스트 `${POSTGRES_PORT:-5432}:5432`, named volume postgres_data와 기본 Compose 네트워크를 사용한다. collectors/operations는 선택 프로필이며 기본 수집 시작이 아니다. 1단계에서는 Docker 메타데이터를 읽기 확인하고 Spring 개발 Compose만 실행했다. Collector DB 접속·설정·리소스 변경은 하지 않았다.

배포 시 실제 기존 네트워크 이름을 읽기 전용 확인한 뒤 Spring 서비스만 해당 external network에 연결해 postgres:5432를 사용한다. 네트워크 이름을 추측하거나 PostgreSQL/volume을 중복 정의·교체하지 않는다. 호스트에서 실행하는 Spring은 검토된 호스트 공개 포트를 사용한다. Spring은 독립 포트 8080을 기본으로 하며 공개 포트 충돌·권한 전환·DB 준비 상태를 배포 때 확인한다. 운영 Dockerfile/Compose는 배포 단계에 작성한다. 격리 개발 DB의 Compose는 이 배포 연결과 별개다.

## Spring DB 기반과 회원 정책

### 환경과 Migration 경계

`compose.dev.yml`의 프로젝트는 `chozy-spring-dev`, 컨테이너는 `chozy-spring-dev-postgres-1`, volume은 `chozy-spring-dev_spring_dev_data`, network는 `chozy-spring-dev_spring_dev`다. PostgreSQL 16을 `127.0.0.1:55432`에만 공개한다. Collector의 5432·`product-data-collector_postgres_data`·`product-data-collector_default`와 연결하거나 공유하지 않는다. stop/down 시 volume은 보존하며 `down -v`는 사용하지 않는다. 이 개발용 관리자 계정은 격리 개발에 한정하며 운영 권한 모델로 복제하지 않는다.

실행·연결·검증 명령은 [README](../README.md#격리-개발-db-실행)에 둔다. 개발 연결 정보는 `CHOZY_DEV_DB_NAME`, `CHOZY_DEV_DB_USERNAME`, `CHOZY_DEV_DB_URL`, `CHOZY_DEV_DB_PASSWORD` 환경변수로 관리한다. DB명·사용자·로컬 주소에는 안전한 기본값이 있으며 안전 검증은 문서의 canonical 대상만 허용한다. 비밀번호 기본값은 없다. 운영 접속 환경변수 `CHOZY_DB_*`와 분리한다. `dbdev`의 datasource URL/사용자가 달라지거나 offline/dev/prod/database와 혼용하면 bean 생성 전에 실패한다. Flyway 별도 URL/계정 주입도 거부한다. 접속 실패는 Hikari 제한 시간과 초기 JPA/Flyway 검증에서 기동 실패로 처리하며 offline으로 자동 전환하지 않는다.

Flyway core와 PostgreSQL 모듈은 Boot BOM 버전을 사용한다. `V1__create_chozy_schema.sql`은 스키마 경계, `V2__create_members.sql`은 회원 테이블을 담당한다. Flyway가 history 배치를 위해 스키마를 먼저 만들므로 V1은 `IF NOT EXISTS`다. 이력은 `chozy.flyway_schema_history`에 checksum·버전·성공 여부로 관리한다. validate-on-migrate=true, baseline-on-migrate=false, clean-disabled=true다. 적용된 SQL을 수정하지 말고 V3 이후 migration을 추가한다. Collector `schema_migrations`와 기존 DDL은 참조하거나 수정하지 않는다. 운영 서버 공동 사용 여부와 관계없이 스키마·소유자·runtime 권한을 분리해야 하며 이번 단계는 운영 적용을 하지 않는다.

### 최소 회원 데이터와 중복 정책

`chozy.members`는 API의 숫자 userId에 대응하는 bigint identity PK, login_id, password_hash, nickname, email, 필수 약관/개인정보 동의 시각, 생성/수정 시각, nullable verified_di_hash/di_verified_at을 갖는다. password_hash에는 향후 PasswordEncoder 결과만 저장한다. 원문 비밀번호·전화번호·원본 DI는 저장하지 않는다. updated_at 갱신은 향후 수정 유스케이스가 담당하며 자동 DB trigger는 없다. 약관 버전·탈퇴/재가입·보관 정책은 인증 구현 전에 확정한다.

- loginId: 앞뒤 Unicode 공백 제거 후 Locale.ROOT 소문자화. 저장값은 ASCII `[a-z0-9][a-z0-9_.-]{0,99}`로 제한한다. 정규화된 값에 Unique 제약을 두며 비정규 저장은 CHECK로 거부한다. 이는 이번 DB 정책이며 원본 명세에는 길이/문자/대소문자 정책이 없으므로 2단계 API 검증과 UI 계약을 맞춘다.
- email: 앞뒤 공백 제거 후 전체 주소 소문자화, 내부 공백 불허. DB는 소문자 canonical 값·최소 @ 구조와 Unique를 강제한다. 정규 형식의 완전한 검증은 향후 DTO에서 수행한다. 로컬 파트도 대소문자를 구분하지 않는 서비스 정책이며 공급자별 점 제거·plus alias 병합은 하지 않는다. 수정 시에도 같은 정책과 Unique를 적용한다. 국제화 주소 지원 범위는 후속 확정한다.
- 사전 중복 확인 API는 사용자 안내용이다. 동시 가입의 최종 판정은 DB Unique다. 향후 서비스는 제약 위반 트랜잭션을 rollback하고 확정된 오류 계약으로 변환해야 한다.
- DI: 미인증 회원의 두 필드는 모두 NULL이다. 두 필드가 함께 있을 때만 허용하고 verified_di_hash에는 Unique를 적용한다(NULL 여러 개 허용). 컬럼은 32~128자의 URL-safe 문자열을 임시 저장 계약으로 예약했으며 해시 알고리즘을 확정한 것이 아니다. DB는 인증 진위를 판정할 수 없다. 신뢰한 업체 검증 결과만 서버 내부에서 기록해야 하며 현재 Entity에는 DI 입력 경로가 없다. 통합 테스트의 합성 문자열은 폐기되는 테스트 DB의 제약 fixture일 뿐 개발 DB에 인증 회원을 만들지 않는다.
- 본인인증이 없으면 서로 다른 아이디와 이메일을 쓰는 동일인의 중복 가입까지 막을 수 없다. Unique 제약과 실제 본인인증은 별개이며 전화번호 일치만으로 동일인이나 DI로 취급하지 않는다.

Entity는 `member.entity`, Repository는 `member.repository`에 최소한만 두며 API DTO와 분리한다. 트랜잭션은 Spring JPA transaction manager를 기반으로 향후 Service의 유스케이스 경계에 둔다. Controller·가입 Service는 없다. Collector 상품 Entity는 만들지 않으며 완료 추천·시장 snapshot 조회는 JDBC/SQL 경계로 유지한다.

### 미구현 인증과 원본 차이

기획서 2.1(인쇄 4쪽)은 휴대폰 본인인증 필수·DI 해시 기반 1인 1계정과 소셜 첫 로그인에서도 인증을 요구한다. 반면 회원가입 API 원본은 loginId/password/nickname/email/termsAgreed/privacyAgreed만 받으며 인증 결과 계약이 없다. 기획서는 소셜 가입 시 아이디·비밀번호가 없다고 명시하지만 현재 테이블은 로컬 계정용 필수 컬럼이다. 소셜 구현 시 credential 분리 또는 nullable 정책 migration을 검토해야 한다. 원본 자료를 임의 변경하지 않고 가입 구현 전에 계약을 조정한다.

1. 휴대폰 본인인증 API 연동·DI 발급/검증은 미구현이다. 업체 선정, 실제 DI 응답 필드·사이트 범위·재사용/만료/서명 검증 계약을 확인해야 한다.
2. 원본 DI를 저장하지 않는 키 기반 해시(HMAC 등), key 보관/버전/회전, canonical 인코딩을 검토하고 업체 선정 후 확정한다. 임시 해시를 생성해 인증값으로 사용하는 경로는 없다.
3. 동일 DI의 1인 1계정 보장과 기존 회원 연계/충돌/탈퇴 후 재가입 정책을 정해야 한다. 가입 요청의 미검증 DI를 신뢰하거나 아이디 중복 검사만으로 1인 1계정을 보장했다고 처리하지 않는다.
4. 카카오·네이버 로그인과 기존 계정 연결은 미구현이다. 후속 외부 계정 테이블에는 `(provider, provider_user_id)` Unique가 필요하다. 동일 DI로 검증된 회원은 별도 계정 생성보다 기존 회원 연결을 우선 검토하되 본인 확인과 계정 탈취 방지 정책을 확정한다. 이메일/전화번호 일치만으로 자동 연결하지 않는다.
5. 실제 회원가입·로그인 API, JWT 발급/검증, Refresh Token 저장/재발급, 이메일 인증·외부 인증 서비스는 전부 미구현이다. 기획서에서는 이메일 인증을 삭제하고 알림용 필수 입력으로 변경했으므로 이메일 인증 도입 여부도 별도 결정한다.

### 검증 원칙

기본 Maven verify는 기존 18개 회귀와 DB 설정 안전 검증을 DB 없이 실행한다. `-Pdb-test verify`는 Testcontainers가 생성한 PostgreSQL 16 URL만 사용하고 Flyway를 적용한 뒤 JPA validate·저장/조회를 확인한다. 운영/개발 DB URL은 테스트 입력으로 받지 않는다. Unique/동시 요청·이메일 canonical CHECK·nullable DI와 합성 DI Unique·중복 실패 후 전체 rollback 및 연결 복구·Migration 재실행을 검증한다. 테스트 데이터는 폐기 컨테이너에만 저장하고 실제 개발 DB를 truncate/clean하지 않는다. Docker가 없을 때 성공으로 건너뛰지 않는다.

## 자료 차이와 미확정 사항

- 서버 참고 자료는 PDF 1개와 Markdown 41개(실제 URL이 있는 API 39개, 템플릿 2개)다. 공통/인증 에러 표는 서버 참고 폴더에 없어 Collector `docs/references/API 명세서/에러 코드` 원본을 읽었다. 원본 자료는 수정하지 않았다.
- 오류 envelope는 빈 템플릿의 A007 예시에만 명시되어 있다. errors 항목의 세부 필드/값은 정의되지 않아 빈 배열로 유지한다. 새 traceId/timestamp/path 필드를 만들지 않았다. A007은 코드 표에 없어 등록하지 않았다.
- 토큰 미제공에 대한 별도 에러 코드가 없어 현재 인증 진입 실패는 A004를 사용한다. JWT 구현 전에 누락/만료/서명 실패 정책과 406 등 표에 없는 HTTP 오류의 코드 정책을 확정한다. 현재 미분류 MVC 오류는 C004로 안전하게 처리한다.
- 기획서의 휴대폰 본인인증 필수/DI 해시 정책이 회원가입 DTO 명세에는 빠져 있다. 업체 계약·필드와 함께 가입 구현 전에 정합성을 맞춘다. 소셜 로그인/이메일 발송 계약도 미정이다.
- 기획서의 과거 수집/호출 가정은 최신 Collector CONTEXT/DATABASE/API_CONTRACTS와 실제 코드의 운영 상태를 대체하지 않는다. 예: 도매매 Private 승인/지속 처리, 90일 coverage, 운영 권한 전환은 완료로 보지 않는다.

## 코드와 테스트 배치

Java는 UTF-8, 공백 2칸을 사용하며 Maven XML은 공백 4칸을 사용한다. `.editorconfig`를 기준으로 IDE 설정을 맞추고, `.gitattributes`로 Windows 실행 스크립트는 CRLF, 소스와 Unix Wrapper는 LF를 유지한다. 참고 원본은 포맷 대상으로 삼지 않는다.

테스트 패키지는 검증하는 운영 코드의 패키지를 따른다. 애플리케이션 기동 검증은 루트의 `ChozyServerApplicationTest`, 공통 응답/예외/보안 검증은 `common`의 각 책임 아래에 둔다. 여러 책임을 하나의 FoundationTest에 계속 쌓지 않는다. 테스트 fixture는 `support`에 두고 `@TestComponent`로 일반 스캔에서 제외한 뒤 필요한 테스트에서만 명시적으로 가져온다. 업무 API가 추가되면 해당 기능의 같은 패키지에 테스트를 추가한다.

## 검증과 다음 API 개발

`./mvnw.cmd -B verify`는 외부 API·DB 없이 공통 응답, MVC 오류, 민감정보 비노출, 보안 차단, CORS, OpenAPI와 DB 인프라 부재를 검증한다. 테스트 전용 Probe Controller는 src/test에만 두며 배포 JAR에는 포함되지 않는다. 데이터/외부 시스템 통합 검증은 별도 격리 환경과 명시적인 자격증명으로 추가한다. 운영 DB로 실패를 해결하지 않는다.

다음 API는 원문 method/path/request/response/JWT부터 확인하고 해당 기능의 Controller·DTO·Service·필요한 Repository만 추가한다. 인증 규칙과 소유권, 실제 HTTP 상태/envelope 일치, 정상/검증/인증/권한 오류 테스트를 함께 작성한다. 자체 데이터 변경은 전용 migration으로 리뷰한다. 완료 발행 조회와 실시간 호출은 별도 통합 책임으로 유지한다. 구현하지 않은 API를 문서나 코드에 엔드포인트로 등록하지 않는다.

버전 근거: [Spring Boot 3.5 공식 문서](https://docs.spring.io/spring-boot/3.5/), [springdoc 호환성 안내](https://springdoc.org/v2/), Maven Central의 실제 배포 artifact 확인. Boot 3.x 요구를 유지했으며 배포 전 해당 계열 지원 정책과 보안 패치를 재검토한다.
