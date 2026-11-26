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
- 전체 API의 Swagger 테스트는 [프론트엔드 API 테스트](docs/API_TESTING.md#swagger에서-개발-api-테스트)를 참고합니다.
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

DB가 이미 실행 중이면 [Spring 기동](docs/API_TESTING.md#swagger에서-개발-api-테스트)으로 넘어갑니다. `dbdev`는 기동 시 이 개발 DB에 대기 중인 Flyway 마이그레이션을 적용합니다. 보존할 개발 데이터가 있으면 기동 전 백업을 확보합니다.

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
- [Collector 현재 상태](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/CONTEXT.md): 수집·발행 진행 상황
- [기능별 API 호출량과 한도](docs/API_USAGE.md) — 외부 호출 수, 공식·실측·내부 운영값
- [분석·리포트](docs/ANALYSIS.md) — 스크리닝, 분석 작업, 재계산
- [계정·사용량](docs/ACCOUNTS.md) — 가입, 토큰, 회원 데이터, 무료 횟수
- [관심상품·알림](docs/FAVORITES_NOTIFICATIONS.md) — 등록, 판매 결과, 설정, 읽음 처리
- [API 테스트](docs/API_TESTING.md) — Swagger와 시장 데이터 검증

작업 결과는 Git에서 제외한 프로젝트 최상위 `temp.md`에 최신 내용으로 덮어씁니다. 변경으로 진척이나 제약이 달라지면 CONTEXT를 갱신합니다.
