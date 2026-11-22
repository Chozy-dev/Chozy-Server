# 상품 URL 정밀분석 요청 API

API Path: /search/analyses/url
HTTP 메서드: POST
도메인: 검색
상태: 시작 전

### 상품 URL 정밀분석 요청 API

> 도매매·오너클랜 상품 URL을 기반으로 정밀분석을 요청합니다. 도매꾹 링크는 도매매 상품으로 전환하며, 스크리닝에서 선택한 키워드가 있으면 분석 키워드로 우선 사용합니다.
> 

### Authentication

필수(JWT)

## Request Header

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `Authorization` | String | O | `Bearer {accessToken}` |

## Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `url` | String | O | 정밀분석할 상품 URL |
| `keyword` | String | X | 스크리닝에서 선택한 분석 키워드 |
| `selectedOptionId` | String | X | 분석할 판매 옵션 식별자. 옵션별 가격이 달라 선택이 필요한 경우 필수 |

```json
{
  "url": "https://domeme.domeggook.com/12345678",
  "keyword": "캠핑 랜턴",
  "selectedOptionId": "OPT_RED"
}
```

## Response Form Example

### Success

```json
{
  "status": 202,
  "message": "상품 정밀분석 요청이 접수되었습니다.",
  "data": {
    "analysisId": 1024,
    "reportId": null,
    "source": "DOMEME",
    "productId": "12345678",
    "productName": "캠핑 미니 랜턴 충전식",
    "keyword": "캠핑 랜턴",
    "selectedOptionId": "OPT_RED",
    "analysisStatus": "PROCESSING",
    "requestedAt": "2026-11-14T04:00:00+09:00"
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `analysisId` | Number | 정밀분석 작업 식별자 |
| `reportId` | Number/null | 생성된 리포트 식별자. 분석 완료 전 null |
| `source` | String | 실제 분석 소싱처 (`DOMEME`, `OWNERCLAN`) |
| `productId` | String | 소싱처의 상품번호 |
| `productName` | String/null | 조회된 상품명 |
| `keyword` | String/null | 분석에 우선 사용할 키워드 |
| `selectedOptionId` | String/null | 분석에 적용할 옵션 식별자. 옵션 선택이 없으면 null |
| `analysisStatus` | String | 분석 상태 (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`) |
| `requestedAt` | DateTime | 분석 요청 시각 |

옵션별 가격이 다른 상품은 먼저 `POST /search/products/resolve-url`에서 옵션을 조회하고 `selectedOptionId`를 전달합니다. 
분석이 성공적으로 완료되어 리포트가 생성될 때만 무료 횟수 1회를 차감하며, 분석 실패 시에는 차감하지 않습니다. 
기존 리포트 상세 조회는 차감하지 않습니다. 
`[지금 데이터로 다시 분석]`은 기존 리포트의 URL·옵션·분석 키워드로 이 API에 새 요청을 보내며, 새 리포트 생성에 성공하면 1회 차감합니다.
데이터랩 한도를 100% 소진한 경우에는 트렌드·지속성을 제외하고 리포트를 생성하되, 무료 횟수는 차감하지 않습니다.