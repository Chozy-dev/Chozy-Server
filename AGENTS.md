# 작업 규칙

- 먼저 `README.md`, `docs/BACKEND.md`와 해당 API의 `docs/references/` 원본을 확인한다.
- Java 21/Maven Wrapper를 사용한다. 검증은 `./mvnw.cmd -B verify`로 수행한다.
- 기본/테스트 환경은 DB·외부 API에 접속하지 않는다. 운영 DB, Docker, Collector 배치나 migration을 임의 실행하지 않는다.
- 기능별 패키지를 사용하고 미구현 Controller·DTO·Entity·가짜 인증을 추가하지 않는다.
- Collector와 backups는 별도 허가 없이 변경하지 않는다.
- 작업 결과는 채팅창에 상세 보고하지 않고 이 프로젝트 최상위 `temp.md`에 한국어 핵심 위주로 작성한다. 작성할 때마다 기존 내용을 전부 지우고 최신 작업 결과만 파일 전체에 덮어쓴다. 이전 결과에 이어 쓰거나 작업 이력을 누적하지 않는다. 채팅에는 완료 여부와 파일 위치만 간단히 알린다.
- `temp.md`는 `.gitignore`로 제외하고 커밋하지 않는다. 유지보수 규칙은 `docs/BACKEND.md`에 모아 중복 보고서를 만들지 않는다.

- 사용법은 README, 아키텍처·개발 규칙은 docs/BACKEND.md, 작업 보고는 temp.md에 기록한다. 같은 내용을 여러 문서에 복제하지 않는다.
- 테스트는 운영 코드의 패키지/책임에 맞춰 배치하고 테스트 fixture는 support에서 명시적으로 가져온다. 편집 시 .editorconfig를 따른다.
