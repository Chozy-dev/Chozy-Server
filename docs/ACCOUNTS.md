# 계정 인증과 사용량

## 일반 계정 인증

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

Spring Security OAuth2 JOSE의 Nimbus 구현을 사용한다. HS256만 허용하며 환경변수 `JWT_SECRET_BASE64`에 CSPRNG로 생성한 32바이트 이상의 키를 Base64로 넣는다. DB 프로필은 키 미제공·잘못된 인코딩·짧은 키·0 이하 수명에 기동 실패한다. 개발/운영 기본 비밀키는 없다. 키·비밀번호·토큰 원문은 로그/오류 응답에 넣지 않고 민감 DTO의 toString도 감춘다.

issuer=chozy, sub=회원 ID, type=access/refresh, 임의 UUID jti, iat/exp를 서명한다. 서명/알고리즘/issuer/유형/필수 claim/시각을 검증하며 시계 유예는 없다. `JWT_ACCESS_SECONDS` 기본 3600(1시간), `JWT_REFRESH_SECONDS` 기본 1209600(14일)이다. 운영은 TLS 및 시간 동기화가 필요하며 키 변경은 기존 토큰을 무효화한다. 키 버전/무중단 교체 정책은 후속 과제다. 외부 인증 서버·Redis는 도입하지 않았다.

V3 `V3__create_refresh_tokens.sql`은 chozy.refresh_tokens(id, member_id FK, token_hash UNIQUE, expires_at, created_at, revoked_at)와 회원/만료 인덱스를 추가한다. 고엔트로피 서명 토큰의 SHA-256 해시만 저장하며 원문은 응답 시 전달한다. 로그인마다 별도 토큰을 생성해 복수 기기 로그인을 허용한다. 서버 재시작 후에도 같은 키와 DB로 검증한다.

재발급은 Access 없이 Refresh의 서명·만료·유형 및 저장 해시/소유자/유효 상태를 확인한다. PESSIMISTIC_WRITE 행 잠금과 단일 트랜잭션으로 이전 토큰 revoke와 새 토큰 저장을 원자적으로 수행한다. 동시 재발급은 하나만 성공하며 이전 토큰 재사용은 A004다. 재사용 시 후속 토큰까지 자동 폐기하는 family 정책은 적용하지 않았다. 로그아웃은 Access 회원 ID와 Refresh 소유자를 대조한 뒤 같은 행 잠금으로 무효화한다. 타인 토큰은 C006이며 해당 토큰은 보존된다.

일반 로그아웃은 해당 Refresh를 폐기하며 기존 Access는 남은 수명(기본 최대 1시간) 동안 유효하다. 비밀번호 변경은 회원 단위 자격 증명 버전과 Refresh 폐기를 적용한다. 만료·폐기 행은 감사와 재사용 검증을 위해 보존하며 만료 인덱스로 정리 작업을 확장할 수 있다. 보존 기간과 정기 삭제는 운영 전 확정한다.

### 오류 계약 보완

Access 만료=A003/401, Refresh 만료=A005/401, 위조·잘못된 형식·서명·유형·누락 인증=A004/401이다. 로그인 ID 부재와 비밀번호 불일치는 동일 A004 응답이며 부재 계정에도 dummy BCrypt 검증을 수행한다. Refresh 미등록/재사용/로그아웃 폐기도 인증 정보가 유효하지 않으므로 기존 의미에 맞는 A004를 사용한다. 중복=C002, 입력=C001, 타인 Refresh 및 운영 가입 차단=C006이다. A006은 SMS 코드 의미이므로 재사용하지 않았다. 원본에는 로그인 실패/Refresh 재사용/가입 차단 전용 코드가 없으며 **전용 코드를 확정한 것으로 취급하지 않는다.** 향후 프론트와 세부 코드 계약을 보완한다.

### 회원탈퇴

JWT `DELETE /my/account`는 토큰 회원을 잠근 뒤 현재 비밀번호를 검증한다. 비밀번호가 틀리면 C001/400이다. 성공 시 V11의 `withdrawn_at`과 자격 증명 버전을 갱신하고 모든 Refresh Token을 동일 트랜잭션에서 폐기한다. 로그인·Access 인증·Refresh 회전은 활성 상태를 검증한다. 이미 접수된 분석 작업은 기존 작업 처리 정책을 따른다.

현재 비활성 계정의 식별자와 관련 데이터를 보존한다. 보관 기간과 삭제·비식별화, 동일 식별자로 재가입하는 정책은 공개 전에 확정해야 한다. 현재 Unique 제약은 탈퇴 계정에도 적용한다.

## 마이페이지와 무료 정밀분석 사용량

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

- 성공한 **새 리포트**의 저장 트랜잭션에서 `recordCreatedReport(memberId, reportId, dataLabQuotaExhausted)`를 호출한다. PostgreSQL의 기본 READ COMMITTED 트랜잭션을 사용한다. 같은 회원의 차감 검증과 원장 기록은 회원 행 잠금으로 직렬화하고, 리포트 ID의 기본 키로 재시도 중복을 막는다. 한도 초과 예외가 발생하면 리포트 저장도 롤백한다.
- 실패, 저장 리포트 열기, 옵션 변경과 재계산은 원장에 기록하지 않는다. 데이터랩의 공유 호출 한도가 100% 소진되어 트렌드·지속성을 제외한 리포트는 신뢰할 수 있는 연동 결과로 면제 여부를 전달한다. 면제 기록은 무료 횟수 집계에서 제외한다. 접수·예약 정책은 [정밀분석 작업과 저장](ANALYSIS.md#정밀분석-작업과-저장)을 따른다. 직접 입력 엔진의 공동 데이터랩 예산은 [시장 데이터](ANALYSIS.md#직접-입력-시장-데이터)를 따른다.
- 스크리닝은 해당 회원의 KST 월 구간에 저장된 실행 행을 센다. 관심상품은 V8 등록 API와 연결되어 해당 회원의 활성 `INTERESTED`와 `SELLING` 등록 행을 센다.
