# 관심상품 상태 변경 API

API Path: /favorites/{favoriteId}/status
HTTP 메서드: PATCH
도메인: 관심 화면
상태: 시작 전

### 관심상품 상태 변경 API

> 관심상품의 상태를 관심중 또는 판매중으로 변경합니다. 기존 상품 데이터와 추적 기록은 유지하며 상태만 변경합니다.
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
| `favoriteId` | Number | O | 상태를 변경할 관심상품 ID |

## Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | O | 변경할 상태 (`INTERESTED`, `SELLING`) |

```json
{
  "status": "SELLING"
}
```

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "관심상품 상태가 변경되었습니다.",
  "data": {
    "favoriteId": 501,
    "previousStatus": "INTERESTED",
    "status": "SELLING",
    "counts": {
      "interested": 1,
      "selling": 2,
      "total": 3
    },
    "updatedAt": "2026-11-17T15:10:00+09:00"
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `favoriteId` | Number | 관심상품 고유 ID |
| `previousStatus` | String | 변경 전 상태 |
| `status` | String | 변경 후 상태 |
| `counts` | Object | 변경 후 상태별 상품 개수 |
| `counts.interested` | Number | 관심중 상품 개수 |
| `counts.selling` | Number | 판매중 상품 개수 |
| `counts.total` | Number | 전체 관심상품 개수 |
| `updatedAt` | DateTime | 상태 변경 시각 |

하나의 상품은 관심중·판매중 중 한 상태에만 속합니다. 상태 변경 시 상품을 새로 생성하지 않으므로 전체 관심상품 개수는 변하지 않습니다.

등록 시점 점수·공급가, 선택 옵션, 판매 결과 기록 및 추적 이력은 유지합니다.

이미 같은 상태인 상품에 동일한 요청을 보내도 정상 응답하며 중복 데이터를 생성하지 않습니다. 본인의 관심상품만 변경할 수 있습니다.