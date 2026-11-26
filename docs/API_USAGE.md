# 기능별 API 호출량과 외부 한도

기준일 2026-11-27. Spring의 REST 요청과 공급사에 보내는 HTTP 요청을 따로 센다. 표의 기본 호출량은 캐시가 비어 있고 재시도 없이 필요한 조회를 완료하는 경우다. Collector의 수집·점수·발행 비용은 홈 화면 요청에 다시 발생하지 않는다.

[서비스 기획설명서](references/Chozy_서비스_기획설명서.pdf) §3~7·§9~10, 양 저장소의 `docs/references/` API 명세, [Collector 설계서 §7.2](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/references/Chozy_DataCollector_설계서_실측반영_20261119.md#72-기획서에-적힌-한도와-유의사항), 현재 컨트롤러·클라이언트·수집 코드를 대조했다.

## Spring 요청당 호출량

| 기능 | Chozy REST 요청 | 현재 외부 HTTP | 기획·미연동 경로 |
| --- | --- | --- | --- |
| 직접 입력 정밀분석 | `POST /search/analyses/manual` 1회 | **보통 4회, 최대 5회**. 검색광고 `GET /keywordstool` 1회 → 쿠팡 `GET products/search` 1회(결과가 비고 분석 키워드가 다르면 1회 추가) → HUB `POST /search-trend/v1/search` 2회(다년 완결 월 1회, 최근 56일 일간 1회) | 실데이터 엔진은 설정으로 활성화한다. 추가 예정 조회는 아래 표 참고 |
| 상품 URL 판별 | `POST /search/products/resolve-url` 1회 | **0회**, 상품 조회 제공자 미연동. 지원 URL은 제공자가 필요하면 503 | 유효 URL에 도매매 `getItemView` 또는 오너클랜 `item` 1회가 기획 기준. 도매꾹 링크도 같은 번호의 도매매 상품을 확인 |
| 상품 URL 정밀분석 | `POST /search/analyses/url` 1회 | **0회**, 소싱처 조회·분석 엔진 미연동 | 기획 §6.11은 리포트당 상품 상세 1회와 시장 호출을 산정했다. 현재 접수 코드가 URL 판별을 다시 수행하므로 판별→접수→분석에서 상세를 몇 번 재사용할지는 연동 구현에 달려 있다 |
| 진행 상태·저장 리포트 | `GET /search/analyses/{analysisId}/status` 폴링 N회 + 완료 후 `GET /search/reports/{reportId}` 1회. 최근 기록 `GET /search/analyses/recent` 페이지당 1회, 의견 `POST /search/reports/{reportId}/feedback` 제출당 1회 | 상태·조회·의견 각각 **0회**. 저장된 작업과 리포트 조회. 다시 분석하면 새 접수 1회와 해당 엔진의 호출 비용 발생 | 폴링 횟수는 화면 주기와 완료 시간에 따라 달라짐 |
| 리포트 추가 입력·재계산 | `PATCH /search/reports/{reportId}/inputs` 1회, 대분류 선택용 `GET /search/coupang-categories` 1회 | 판매가·대분류·CPC 변경과 대분류 목록 **0회**. 키워드·옵션 변경용 실제 조회 제공자는 미연동 | 키워드 변경은 영향받는 시장 조회, 옵션 변경은 상품·옵션 조회가 필요할 수 있다. 캐시와 연결 범위에 따라 횟수가 달라지며 새 무료 리포트는 차감하지 않는다 |
| 키워드 스크리닝 | `POST /search/screenings` 1회, 최근 기록 `GET /search/screenings/recent` 1회 | **0회**, 제공자 미연동. 유효 요청에 필수 외부 데이터가 없으면 503 | 고유 키워드 (K)개, 최대 10개. `ceil(K/5)`회씩 검색광고 `RelKwdStat` + HUB 검색어 트렌드. 10개면 각 2회로 합계 4회. 트렌드는 13개월 일간 요청을 재사용하고 기획상 당일 예산 60%를 넘으면 생략 |
| 쇼핑 반응 분석 | `GET /search/categories` 1회, `POST /search/screenings/{screeningId}/shopping-analysis` 카드·분야 선택당 1회 | **0회**, 쇼핑인사이트 제공자 미연동. 분야 목록은 로컬 11개 | 선택한 카드당 HUB `POST /shopping/v1/category/keywords` 1회. 기존 스크리닝 검색 추이 재사용 |
| 홈 추천·쿠팡 베스트·네이버 시즌·비교 | `GET /home/products/recommended`, `GET /home/keywords/coupang`, `GET /home/keywords/naver`, `GET /home/keywords/compare` 각각 조회당 1회 | 각각 **0회**. Collector 완료 발행본 조회. 마진 계산기는 입력·기본 요율을 사용 | Collector에서 공통 데이터를 수집하는 비용은 다음 절 참고 |
| 관심상품·알림 | `/favorites` 등록·목록, `/favorites/{favoriteId}` 조회·삭제, 하위 `/status`·`/sales-results`, `/home/notifications` 목록·개별/전체 읽음, `/my/notification-settings` 조회·변경 각각 동작당 1회 | 각각 **0회**. 저장 데이터 사용. 시장 갱신·외부 알림 전송 미연동 | 공급가·품절·공급 점수는 Collector 재사용으로 추가 0회. 시장가가 24시간 넘은 상세 열람은 쿠팡 1회, 주간 갱신은 상품당 1회(사용자별 5분 슬롯, 최대 50건·분당 10회 가안). 트렌드·지속성은 고유 키워드 5개당 HUB 2회, 일 200회 내부 배정 가안. 경쟁·검색량을 새로 조회할 때 검색광고는 5개당 1회 기준으로 산정 가능하며 주간 정확한 호출 정책은 미확정 |
| 계정·사용량 | `/auth` 가입·중복확인·로그인·재발급·로그아웃, `/my` 대시보드·프로필·비밀번호·광고동의·탈퇴, `GET /home/report-usage` 각각 동작당 1회 | 각각 **0회**. 본인인증·소셜 로그인은 미연동 | 공급사·인증 사업자 선택 후 호출량 산정 |

Spring 실데이터 검증(11월 27일)의 완료 작업은 쿠팡 1회, 검색광고 1회, 트렌드 2회였다. 같은 키의 성공 응답은 프로세스별 최대 256개를 10분 보관하며, 적중한 조회는 외부 HTTP 0회다. 간격 제한이 걸리면 HTTP를 보내기 전에 최대 2회 더 예약을 시도한다. 외부 HTTP 자체의 자동 재시도는 없다. 데이터랩 예약 결과가 `exhausted:day`면 트렌드·지속성을 생략하고 해당 리포트의 무료 횟수를 면제한다. 현재 Spring 코드에서 REST 전체에 공통으로 적용되는 요청 속도 제한은 확인되지 않았다. 무료 월 3회는 회원별 리포트 사용량이며, KST 기준 매월 1일 초기화된다. [Spring 검증 기록](https://github.com/Chozy-dev/Chozy-Server/blob/main/docs/CONTEXT.md#최신-검증)

### 리포트 기획의 추가 호출

| API | 기획 §6.11의 리포트당 요청 수 | 현재 직접 입력 |
| --- | --- | --- |
| 도매매 `getItemView` 또는 오너클랜 `item` | URL 상품 상세 1회. 90일 이력·공급사 집계는 DB | 직접 입력은 공급처 조회 0회 |
| 쿠팡 Wing 추천·메타 | 각 1회. 오너클랜은 추천 생략. 카테고리 코드 확보 후 메타 조회 | 0회. 내부 대분류·기본 수수료 사용 |
| 쿠팡 파트너스 `products/search` | 상품명 1회 + 키워드 가격 비교 최대 4회 | 상품명 1회 + 빈 결과 시 보조 1회. 키워드별 비교 미연동 |
| 검색광고 `RelKwdStat`·`Estimate` | 각 1회. Estimate는 `POST /estimate/average-position-bid/keyword` | 각각 1회·0회 |
| HUB 검색어 트렌드·쇼핑인사이트 | 트렌드 2회, 분야가 있으면 쇼핑인사이트 키워드 성별·연령 0~2회. [성별 조회](https://api.ncloud-docs.com/docs/naver-api-hub-shopping-insight-keyword-gender) · [연령별 조회](https://api.ncloud-docs.com/docs/naver-api-hub-shopping-insight-keyword-age) | 각각 2회·0회 |
| HUB 블로그·카페 검색 | 2회(각 1회), 출시 후 검토 | 0회 |
| LLM·KIPRISPlus | 상표 검색어 추출 0~1회, 상표 조회 0~2회 | 각각 0회. LLM 공급사 및 모델 미확정 |

## Collector 배치당 호출량

페이지 수에는 종료를 확인하는 요청이 포함될 수 있다. 아래 식은 재시도·인증을 별도로 더한다. 페이지별 상세 묶음은 페이지 경계를 넘겨 합치지 않는 경로가 있다.

| 작업 | 외부 HTTP 기본 호출량 |
| --- | --- |
| 쿠팡 일일 베스트 | `GET products/bestcategories/{categoryId}` **카테고리당 1회**. 카테고리 C개면 C회. 요청 limit 최대 100상품, 완료된 카테고리는 같은 작업 재개 시 재사용 |
| 네이버 일일 시즌 키워드 | 테마당 `RelKwdStat`(event·month) **1회** + 검색어 트렌드 **2회**(상위 10키워드를 5개씩, D−56~D−1 일간). 3테마면 **3 + 6 = 9회**. 완료 단계 재개 시 재사용 |
| 네이버·쿠팡 비교, 공급 점수 및 발행 | **0회**. 당일 시장 스냅샷과 저장된 상품·이력 사용 |
| 오너클랜 최초 적재·발견·복구 | `allItems` **페이지당 1회** + `itemsByKeys` **고유 상세 대상 최대 100개당 1회**. `allItems` 최대 1,000개/페이지, 현재 수집 설정 파일의 `page_size`는 500. 전체 필드 거부 시 최소 필드 1회 추가 가능 |
| 오너클랜 일일 변경 및 이력 백필 | `itemHistories` **기간별 페이지당 1회**(7일 이하 구간, 코드 상한 1,000 이벤트/페이지, 실제 설정값 사용). 일일 수집은 `itemsByKeys` **고유 상세 대상 최대 100개당 1회** 추가. 예를 들어 상세 대상 10만 개면 묶음이 꽉 찰 때 상세만 최소 1,000회. 미반환 키 보완·다음 작업 재시도 별도 |
| 오너클랜 인증 | `POST /auth` 토큰 최초 발급·갱신당 **1회**. 유효 캐시 토큰이면 0회. 통합 예산에서는 `auth`로 집계 |
| 도매매 최초 적재·발견·복구 | `getCategoryList` 새 작업당 **1회** + `getItemList` 카테고리·페이지당 **1회**(통합 기본 100건) + 페이지별 `ceil(상세 대상/50)`회의 `getItemView`(통합 기본 50개 묶음). 최초 적재·발견은 기존 유효 상세를 재사용, 복구는 조회 대상 상세를 다시 받음 |
| 도매매 일일 변경 | Private `POST /api/v1/product/_search` **페이지당 1회**(최대 100상품) + `getItemView` **비어 있지 않은 페이지당 1회**(페이지 안에서 고유 번호를 최대 100개 묶음). 정상 경로에서 비어 있지 않은 페이지마다 총 2회. 마지막 빈 응답은 Private 1회만 발생 |
| 도매매 품절·재입고 원본 신호 | 통합 일일 경로 **0회**. 실측한 별도 `getAllSupplyChk` v1.1 경로는 페이지당 1회 + 세션 발급 필요 시 `setLogin` 1회. 날짜·상태·페이지 수에 따라 달라짐 |

오너클랜 일일 기본식은 `이력 페이지 수 + ceil(고유 상세 대상/100) + 인증·보완·재시도`다. 묶음이 덜 차거나 상세를 나눠 실행하면 호출 수가 늘 수 있다. 도매매 일일 기본식은 `Private 페이지 수 + 상세를 조회한 페이지 수 + 재시도`다.

코드 근거는 Spring의 `MarketManualAnalysisEngine`·`MarketApiClient` 및 Collector의 `product_data_collector/operations/adapters.py`, `naver_API/collector.py`, `coupang_API/workflows/best.py`, `ownerclan_API/workflows/{bootstrap,history_sync}.py`, `domeggook_API/workflows/product_sync.py`다.

## 외부 API 호출 한도

| API | 공급사 호출량 한도 | 속도 제한과 확인 범위 |
| --- | --- | --- |
| 쿠팡 파트너스 `products/search` | 2024-12 공개 이용 가이드 p.33은 **시간당 10회**. Chozy는 **분당 50회 테스트 완료**(팀 확인, 기획 §9.1·§9.3). 현재 계정의 시간·일 단위 총량은 미확정 | 과거 100키워드 성공 구간 평균 48.32 RPM. 120 RPM 별도 실측은 52번째에서 HTTP 200 + 본문 `rCode=403`, 메시지는 시간당 초과. 검색 응답 최대 10개. [공식 이용 가이드 p.33](https://partners.coupangcdn.com/partners-guide/partners-guide-20241216163254.pdf#page=33) |
| 쿠팡 파트너스 `bestcategories` | 같은 가이드는 Search·실적 Report 외 API를 **시간당 100회**로 포괄 표기. 베스트 개별·현재 계정 한도는 미확정 | 검색 제한 중 베스트도 `rCode=403`이었던 기록이 있어 공동 예산에 합산한다. 베스트 독립 RPM 및 공급사 합산 방식은 미확정. limit=100은 상품 수로, 실측 81개 반환 사례가 있음. [Collector 검증](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/VALIDATION.md#쿠팡-베스트-실데이터-검증) |
| 쿠팡 Wing 카테고리 추천·메타 | 엔드포인트별 기간 총량 미확인 | 판매자 Open API는 일부 API를 초당 10→5회로 조정한다고 안내. 추천·메타에 적용되는 정확한 값은 미확정. [판매자 API 목록](https://developers.coupang.com/ko/api) · [호출 빈도 공지](https://developers.coupang.com/ko/notices/optimization-and-adjustment-of-open-api-rate-limiteffective-march-17th2026?market=KR) |
| 네이버 검색광고 `RelKwdStat`·`Estimate` | 검색광고 공식 FAQ는 기간 호출량 상한 없음 | Customer ID 기준 가변 제한, 연관 키워드는 IP도 고려. 수치는 비공개이며 초과 시 HTTP 429. RelKwdStat 힌트 최대 5개는 요청 묶음 크기. [공식 FAQ](https://github.com/naver/searchad-apidoc/wiki/FAQ) · [API 명세](https://naver.github.io/searchad-apidoc/) |
| HUB 데이터랩 검색어 트렌드·쇼핑인사이트 | **각각 월 50,000회**. 동일 키로 Spring·Collector가 부르면 API별로 합산 | **키당 50 RPS**, 데이터랩 유형의 플랫폼 전체 상한 500 RPS. 초과는 429. 검색어 트렌드는 요청당 최대 5그룹·그룹당 5개 단어, 쇼핑인사이트 키워드 트렌드는 한 분야·최대 5개 키워드 쌍. [공식 한도](https://guide.ncloud-docs.com/docs/apihub-overview) · [검색어 트렌드](https://api.ncloud-docs.com/docs/naver-api-hub-search-trend) · [쇼핑인사이트](https://api.ncloud-docs.com/docs/naver-api-hub-shopping-insight-keywords) |
| 기존 개발자센터 데이터랩 | 검색어 트렌드·쇼핑인사이트 문서에 **일 1,000회** 표기. 기획은 통합 1,000회로 배분 | 공개 자료에서 정확한 RPS 및 두 API 간 합산을 확인하지 못했다. 기존 키의 유예는 2027-06-30까지. [API 목록](https://developers.naver.com/products/intro/plan/plan.md) · [이관 공지](https://developers.naver.com/notice/article/32530) |
| HUB 검색 블로그·카페 | 검색 카테고리 통합 **월 775,000회** | 키당 50 RPS, 검색 플랫폼 전체 1,000 RPS. 기존 네이버 쇼핑 검색 API는 2026-07-31 종료, HUB 이관 제외. [공식 한도](https://guide.ncloud-docs.com/docs/apihub-overview) · [종료 공지](https://developers.naver.com/notice/article/32564) |
| 네이버 커머스 카테고리 조회 | 기획의 분야 코드 검증에 사용. 현 런타임 0회 | 공식 문서는 API·인증 앱별 가변 초당 제한과 응답 헤더를 안내. 해당 계정의 값 미확정. [공식 제약](https://apicenter.commerce.naver.com/docs/restriction) |
| 오너클랜 Seller GraphQL·인증 | 현재 `item`·`allItems`·`itemHistories`·`itemsByKeys`·`/auth` 기간 한도, 합산 범위 및 리셋 미확정 | Seller PDF v0.11.0(2020-10-21) p.73의 초당 1,000건은 단건 `item` 항목의 과거 안내. p.3의 검색·가격 정렬은 강화된 quota 적용. 현재 정책은 11월 22일 단건이 3초 간격에서도 24번째에 GraphQL 제한된 실측과 별개다. [공식 상품 API](https://www.ownerclan.com/V2/service/api-center-item.php) · [Seller 매뉴얼 ZIP](https://cdn.ownerclan.com/API%20%EB%A7%A4%EB%89%B4%EC%96%BC.zip) · [실측](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/VALIDATION.md#오너클랜-api-지연-실측) |
| 도매꾹 Open `getCategoryList`·`getItemList`·`getItemView` 및 별도 조사 `getItemViewES` | 코드의 Open 기준값 **일 15,000회**는 현행 공개 문서에서 재확인하지 못했다. 현재 로컬 설정은 키별 일 14,000회·시간 9,000회 | 코드 기준은 **분당 180회 미만**, 로컬 120 RPM. 과거 Private 키로 호출한 **`getItemViewES`**는 관측 157.30 RPM에서 성공, 180.38·190.39 RPM에서 429. 이 실측의 적용 범위는 해당 ES 조회 경로다. [Open API 안내](https://openapi.domeggook.com/ko/articles/OPEN-API-이용-방법-bd7265d8) · [상품 상세](https://openapi.domeggook.com/ko/articles/상품상세정보-933abc24) · [ES 상세](https://openapi.domeggook.com/ko/articles/상품상세정보-ES-기반-a4572234) |
| 도매매 Private 상품정보 동기화 `_search` | 공개 안내는 별도 횟수 상한 없이 주기적 동기화를 지원한다고 설명 | 같은 명세에 별도 내부 제한 정책과 과다 호출 차단도 명시. 수치·윈도·리셋 미공개. 승인된 키·등록 IP 필요, 100개는 페이지 크기. [공식 명세](https://openapi.domeggook.com/ko/articles/상품정보-동기화-6b5f900f) |
| 도매매 Private `getAllSupplyChk`·`setLogin` | 공개 명세에서 기간 총량 미확인 | 공개 속도 수치 미확인. 세션 조회는 `date`, `ic`, `pg` 지원. [공식 상태 목록](https://openapi.domeggook.com/ko/articles/전체품절확인목록-47d59af2) · [로그인](https://openapi.domeggook.com/ko/articles/로그인-e4aaf7c2) |
| KIPRISPlus 상표 | 무료 **회원 계정의 전체 상품 합산 월 1,000회**, 매월 1일 초기화 | 공식 FAQ 기준 무료 **50 RPS**, 유료 **100 RPS**. 유료 기본 일 5,320원(VAT 포함), 할인 조건 별도. [공식 FAQ 25번](https://plus.kipris.or.kr/portal/bbs/Faq_info.do?buttonIndex=&pageIndex=3) · [수수료](https://plus.kipris.or.kr/portal/use/paymentMmg.do?menuNo=210112) |
| 상표 검색어 추출용 LLM | 모델·공급사·계정 미정으로 공급사 수치 미확정 | 기획상 리포트당 최대 1회 |

## 공식 안내·실측·기획의 차이

1. **쿠팡 파트너스.** 분당 50회는 팀 실측값이다. 2024 공개 가이드의 Search 시간당 10회와 다르며, 2025-10 공개 이용 가이드는 API 안내 링크를 제공한다. 과거 403의 시간당 초과 문구까지 고려하면 분당 실측을 시간·일 쿼터로 환산할 근거가 없다. 기획의 72,000회/일은 산술 예시다. 베스트 공유 403은 관측 사실이며 정확한 합산 수치는 미확정이다. 과거 실험은 로컬 백업 `backups/collector-cleanup-20261120-133841/files/docs/experiments/API_PROBE_RESULTS.md`의 쿠팡 호출 제한 절에 있다. [2025 공개 이용 가이드](https://partners.coupangcdn.com/partners-guide/partners-guide-20251028182159.pdf)
2. **네이버 데이터랩.** 기획의 하루 합산 1,000회·60%/80% 우선순위·하루 공유 캐시는 내부 설계다. 현재 엔진은 HUB를 사용하고 10분 로컬 캐시와 공동 일 예산 면제 분기를 구현했다. HUB 월 50,000회는 검색어 트렌드와 쇼핑인사이트에 각각 적용된다. 공동 월 예산의 `exhausted:month` 또는 공급사 HTTP 429는 현재 분석 실패 경로에 해당한다. 일 예산 자동 재분배와 기획의 서비스별 임계치는 현재 코드에서 확인되지 않았다.
3. **오너클랜.** 공식 센터는 `itemsByKeys` 5,000개/회를 안내한다. 11월 20일 운영 API는 1,000개 요청에 최대 100개 오류를 반환했고, 11월 21일에는 101개 요청에 오류 없이 1행만 반환했다. 구현의 작업 묶음 상한은 100개다. 오래된 초당 1,000건 표기와 과거 `allItems(first:1)` 180 RPM 시험은 현재 전체 계정의 속도 또는 장기 허용량을 확정하는 근거가 아니다. [Collector 실측](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/VALIDATION.md#오너클랜-호출-정책과-하루-1만-상품-조사)
4. **도매매.** 기획의 Private 승인 전 실패는 11월 27일 로컬 성공(HTTP 200, `result=true`, 1상품·다음 커서)으로 갱신된다. 동기화는 상품별 최신 상태와 변경 탐지에 사용하고 `getAllSupplyChk`의 품절·재입고 원본 이벤트는 별도로 필요하다. 기획의 호출량 약 1/100은 묶음 처리 기대치다. 현 일일 경로는 Private 페이지 외에 Open 상세를 추가로 호출한다. 공식 v4.6 상세는 도매꾹·도매매 데이터를 함께 제공해 `market`이 필수는 아니며, 현재 클라이언트는 `market=supply`를 보낸다. 로컬 성공 기록은 `artifacts/validation/domeggook-private-sync-20261127-020544.json`에 있다. [이전 승인 실패 기록](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/VALIDATION.md#도매매-private-동기화-재호출)
5. **KIPRISPlus.** 기획 당시 전달받은 무료 횟수·속도·기본 요금을 공식 문서에서 확인했다. 공식 FAQ는 무료 상품 전체 합산과 유료 초당 100회도 안내한다.

## Chozy 내부 호출 예산

Spring 직접 입력 개발 검증은 KST 자정 기준 일 예산을 쓴다. 아래 값은 분석 최대 호출량으로 10건까지 계산한 로컬 예약량이다. 실제 시도 가능량은 공급사 잔여 한도에도 제한된다.

| 호출 경로 | 계정 별칭 예시 | 예산 API | 개발용 일 상한 | 최소 간격 |
| --- | --- | --- | ---: | ---: |
| 쿠팡 검색 | `coupang-main` | `default` | 20회 | 1초 |
| 네이버 검색광고 | `naver-main` | `ad` | 10회 | 1초 |
| HUB 검색어 트렌드 | `naver-main` | `trend` | 20회 | 1초 |

운영 쿠팡 검색은 `default`를 예약한다. 베스트는 `best`와 공동 `default`를 한 트랜잭션에서 예약한다. 각 기간의 베스트 상한은 공동 상한보다 작아야 하므로 베스트 수집만으로 리포트 몫을 소진할 수 없다. 네이버 HUB는 `trend`·`shopping`을 API별로 집계하고 같은 키의 `hub` 상한도 함께 예약한다. 네이버 검색광고 `ad`는 별개다. 도매매 `open`·`private`, 오너클랜 `default`·`auth`는 각각 설정된 정책을 적용한다.

공동 예약 함수는 KST의 고정 초·분·시간·일·월 구간과 최소 간격을 지원한다. 쿠팡 개발 예시의 일 20회·최소 1초는 시간당 10회를 보장하지 않는다. 실제 계정의 시간 한도는 확인 후 `hour`로 설정한다. 공급사가 이동 구간을 적용하면 고정 구간 경계의 연속 호출도 고려해 별도 간격을 설정한다. [공동 예약 함수](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/product_data_collector/persistence/shared_budget_schema.py)

Spring과 Collector 운영 경로 및 운영 자격증명을 사용하는 CLI·조사 도구는 같은 예산 DB와 계정 별칭을 사용한다. 수집기 운영 이미지·Compose에서는 공동 예약을 요구하며, 별도 호스트의 CLI·조사 도구에는 `COLLECTOR_REQUIRE_SHARED_BUDGET=true`와 같은 DB·계정 설정을 주입한다. 예약 거부 시 해당 HTTP 시도는 발생하지 않는다. 적용 순서는 [Collector 운영 전환](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/DEPLOYMENT.md#운영-공동-호출-예산-적용)을 따른다.

Collector 예시 설정은 네이버 검색광고 일 3회, 트렌드 일 6회·월 186회, 각각 최소 간격 1초다. 쿠팡 베스트의 배치 호출량·RPM은 운영자가 입력한다. 도매매 Private 통합 예시는 1 RPM, 오너클랜 회복 확인·정상 후 운영 권고는 각각 계정 합산 30 RPM 이하와 60 RPM이다. 운영 예산과 차단 복구 절차는 [Collector RUNBOOK](https://github.com/Chozy-dev/Chozy-DataCollector/blob/main/docs/RUNBOOK.md#공동-호출-예산)에 있다.
