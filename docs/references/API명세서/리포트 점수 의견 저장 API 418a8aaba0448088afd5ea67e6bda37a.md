# 리포트 점수 의견 저장 API

API Path: /search/reports/{reportId}/feedback
HTTP 메서드: POST
도메인: 검색
상태: 시작 전

### 리포트 점수 의견 저장 API

> 사용자가 정밀분석 리포트의 종합점수에 대해 평가한 의견을 저장합니다. 수집된 의견은 향후 점수 가중치·구간·등급 경계 등 점수 산정 기준을 개선하기 위한 데이터로 활용합니다.
> 

### Authentication

필수(JWT)

## Request Header

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `Authorization` | String | O | `Bearer {accessToken}` |

## Request Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | Number | O | 의견을 등록할 정밀분석 리포트 식별자 |

## Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `feedback` | String | O | 점수 평가 (`APPROPRIATE`, `TOO_HIGH`, `TOO_LOW`) |
- `APPROPRIATE`: 적당해요
- `TOO_HIGH`: 너무 높아요
- `TOO_LOW`: 너무 낮아요

```json
{
  "feedback": "TOO_HIGH"
}
```

## Response Form Example

### Success

```json
{
  "status": 201,
  "message": "리포트 점수 의견이 저장되었습니다.",
  "data": {
    "feedbackId": 301,
    "reportId": 2048,
    "productId": "12345678",
    "score": 75,
    "supplyConditionScore": 78,
    "marketConditionScore": 73,
    "feedback": "TOO_HIGH",
    "createdAt": "2026-11-14T04:15:00+09:00"
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `feedbackId` | Number | 저장된 점수 의견 식별자 |
| `reportId` | Number | 평가한 리포트 식별자 |
| `productId` | String/null | 소싱처 상품번호. 직접 입력 분석이면 null |
| `score` | Number | 의견 제출 시점의 종합점수(0~100) |
| `supplyConditionScore` | Number/null | 의견 제출 시점의 공급 조건 점수 |
| `marketConditionScore` | Number/null | 의견 제출 시점의 시장 조건 점수 |
| `feedback` | String | 사용자 의견 (`APPROPRIATE`, `TOO_HIGH`, `TOO_LOW`) |
| `createdAt` | DateTime | 의견 저장 시각 |