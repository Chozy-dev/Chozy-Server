# Chozy Server

위탁 셀러의 상품 소싱 검증 서비스 Chozy의 Spring Boot 백엔드입니다. 프론트엔드 REST API와 사용자 기능을 담당하며, 상품 수집·공급 점수 계산·정기 추천 발행은 별도 Python Collector가 담당합니다.

현재는 **공통 기반·격리 개발 DB·일반 계정 인증·마이페이지 회원 관리·무료 정밀분석 사용량 조회**를 구현했습니다. 기본 실행과 테스트에는 PostgreSQL이나 외부 API가 필요하지 않습니다.

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
| `dbdev` | 격리된 로컬 PostgreSQL, Flyway 적용 | 공개, 구현 API 10개 |
| `database` | 명시적인 접속 설정 필요 | 기본 비활성 |
| `prod` (`database` 포함) | 명시적인 접속 설정 필요 | 기본 비활성, 별도 제어 |

`offline/dev`와 DB 프로필을 함께 지정하지 않습니다. `dbdev`와 `database/prod`도 혼용할 수 없습니다. 운영 DB 연동과 Spring 전용 계정 전환은 미완료입니다. Hibernate DDL·SQL 초기화는 실행하지 않으며 Flyway는 `dbdev`에서만 적용합니다.

### 격리 개발 DB 실행

Collector Compose를 사용하지 않습니다. 아래 명령은 Spring 디렉터리에서만 실행합니다. 먼저 55432 포트와 `chozy-spring-dev` 리소스 이름이 다른 용도로 사용되지 않는지 확인합니다.

```powershell
$env:CHOZY_DEV_DB_PASSWORD='<개발용 비밀번호>'
docker compose -f compose.dev.yml up -d --wait
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dbdev'
# 애플리케이션 종료 후 DB만 중지 (volume 보존)
docker compose -f compose.dev.yml stop
# 다시 시작
docker compose -f compose.dev.yml up -d --wait
```

연결: `127.0.0.1:55432`, DB/사용자 `chozy_spring_dev`. IntelliJ Active profiles는 `dbdev`, 환경변수는 `CHOZY_DEV_DB_PASSWORD`입니다. 비밀번호는 기존 volume 초기화 시 정한 값을 계속 사용합니다. 비밀번호 환경변수 변경만으로 기존 DB 비밀번호가 바뀌지는 않습니다. `.env.dbdev`에 저장한 경우 Compose에는 `--env-file .env.dbdev`를 명시하고 Spring에는 별도 환경변수로 주입합니다. 이 파일은 Git에서 제외됩니다.

```powershell
# 기본 회귀: Docker/DB 없이 실행
.\mvnw.cmd -B verify
# DB 회귀: Testcontainers가 매번 별도 PostgreSQL 16을 생성·정리
.\mvnw.cmd -B -Pdb-test verify
```

DB 통합 테스트는 외부 접속 URL을 받지 않으며 개발 데이터도 삭제하지 않습니다. Docker가 없으면 통합 검증은 실패합니다. 결과는 `target/failsafe-reports/`에 기록됩니다. Migration·회원 정책·후속 인증 설계는 [BACKEND](docs/BACKEND.md#spring-db-기반과-회원-정책)를 따릅니다.

## 프로젝트 구조

```text
Chozy_Server/
├── README.md                    프로젝트 소개·실행·설정·검증
├── AGENTS.md                    자동화 작업 규칙
├── pom.xml                      의존성과 빌드
├── mvnw / mvnw.cmd / .mvn/       Maven Wrapper
├── .env.example                 환경변수 예시
├── .editorconfig                들여쓰기·문자 인코딩 기준
├── .gitattributes               소스와 실행 스크립트 줄바꿈 기준
├── docs/
│   ├── BACKEND.md               아키텍처·개발 규칙·연동 경계
│   └── references/              기획서·API 명세 원본
├── src/main/
│   ├── java/com/chozy/server/
│   │   ├── ChozyServerApplication.java
│   │   └── common/
│   │       ├── config/          OpenAPI 설정
│   │       ├── error/           에러 코드·업무 예외·전역 예외 처리
│   │       ├── response/        성공 응답
│   │       └── security/        접근 정책·CORS·보안 오류 응답
│   └── resources/               공통 및 프로필별 application 설정
├── src/test/java/com/chozy/server/
│   ├── ChozyServerApplicationTest.java
│   ├── common/
│   │   ├── error/               예외 응답 검증
│   │   ├── response/            성공 응답 검증
│   │   └── security/            접근 정책·CORS·설정 검증
│   └── support/                 명시적으로 가져오는 테스트 전용 fixture
└── target/                      빌드 산출물·검증 로그 (Git 제외)
```

업무 API는 `auth`, `home`, `screening`, `analysis`, `report`, `favorite`, `notification`, `member`, `usage` 등 **기능별 패키지**에 추가합니다. 각 기능 안에서 필요한 Controller → Service → Repository와 DTO/Entity를 생성합니다. 패키지별 책임과 Collector·외부 시스템 경계는 [백엔드 개발 규칙](docs/BACKEND.md#패키지와-의존-방향)을 따릅니다.

## 검증

```powershell
.\mvnw.cmd -B verify
# 특정 책임만 검증하는 예
.\mvnw.cmd '-Dtest=GlobalExceptionHandlerTest' test
```

테스트는 공통 응답·MVC 예외·보안 차단·CORS·문서 접근과 DB 인프라 부재를 검증합니다. 실제 DB나 공급처 API를 호출하지 않습니다. 결과는 `target/surefire-reports/`, 실행 JAR는 `target/server-0.0.1-SNAPSHOT.jar`에 생성됩니다.

## 개발 문서

- [백엔드 구조·개발 규칙](docs/BACKEND.md): 패키지 책임, 응답·보안 계약, DB 관리와 외부 연동 경계, 미확정 사항
- [기획서·API 명세](docs/references/): API 구현 시 확인할 원본 자료
- [Collector 현재 상태](../product-data-collector/docs/CONTEXT.md): 수집·발행 시스템의 현재 구현 및 운영 미완료 항목

작업 결과는 프로젝트 최상위 `temp.md`에 기록하며 Git에서 제외합니다. 매번 기존 내용을 전부 지우고 최신 작업 결과로 파일 전체를 덮어씁니다. 이전 결과에 이어 쓰거나 이력을 누적하지 않습니다. 지속해서 유지할 사용법은 README, 설계 규칙은 BACKEND 문서에서 관리합니다.


## 프론트엔드 API 테스트

Swagger에서 인증·회원·홈 API를 테스트하려면 기존 [격리 개발 DB](#격리-개발-db-실행)를 실행하고 `dbdev`로 Spring을 기동합니다. Swagger를 활성화하는 추가 플래그는 필요 없습니다. `dev` 프로필은 DB 없이 실행되므로 업무 API가 등록되지 않습니다.

- API 기본 주소 `http://localhost:8080`
- Swagger UI [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI 3.1 JSON [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

기존 DB 비밀번호와 32바이트 이상 Base64 개발 JWT 키는 환경변수로 전달합니다. 서버를 재시작해도 기존 토큰을 유지하려면 같은 JWT 키를 사용합니다.

```powershell
$env:CHOZY_DEV_DB_PASSWORD='<기존 개발 DB 비밀번호>'
$env:JWT_SECRET_BASE64='<비밀 저장소에 보관한 개발 JWT 키>'
# Swagger UI는 같은 origin을 사용합니다. 아래는 별도로 실행하는 프론트엔드용 예시입니다.
$env:CORS_ALLOWED_ORIGINS='http://localhost:3000,http://localhost:5173'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dbdev' '-Dspring-boot.run.arguments=--server.address=127.0.0.1'
```

첫 실행에 개발 JWT 키가 없다면 32바이트 난수 키를 생성해 개발용 비밀 저장소에서 관리합니다.

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

일반 가입은 본인인증 없는 임시 개발 경로입니다. dbdev만 기본 허용하고 database는 기본 차단, prod는 설정을 켜도 서버에서 차단합니다. 본인인증/DI·카카오·네이버 로그인은 미구현입니다. 로그아웃은 Refresh를 폐기하며 기존 Access는 잔여 만료 시간까지 유효합니다. 정책·미확정 계약·V3 저장 구조는 [BACKEND](docs/BACKEND.md#2단계-일반-계정-인증-구현)를 참고합니다.

`PATCH /my/password` 변경 성공 후에는 기존 Access와 Refresh 모두 차단됩니다. 홈·마이페이지의 무료 정밀분석 한도는 `FREE_MONTHLY_REPORT_LIMIT`에서 설정하며 기본값은 3입니다. 자세한 저장·집계 정책은 [BACKEND](docs/BACKEND.md#3단계-마이페이지와-무료-정밀분석-사용량)를 참고합니다.

```powershell
# DB 없이 기동·공통/JWT 회귀
.\mvnw.cmd -B verify
# Docker 필요: 기존 DB와 독립된 Testcontainers PostgreSQL에서 인증/동시성/DB 재시작 검증
.\mvnw.cmd -B -Pdb-test verify
```
