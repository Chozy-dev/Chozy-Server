# 알림 목록 조회 API

API Path: /home/notifications
HTTP 메서드: GET
도메인: 홈 화면
상태: 시작 전

### 알림 목록 조회 API

> 사용자의 알림 목록을 조회합니다. 읽지 않은 알림을 상단에 표시하고, 최신 알림부터 내림차순으로 반환합니다.
> 

### Authentication

필수 (JWT)

## Request Query Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | Number | X | 페이지 번호. 기본값 1 |
| `size` | Number | X | 페이지당 조회 개수. 기본값 20 |
| `isRead` | Boolean | X | 읽음 여부 필터. 미입력 시 전체 조회 |

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "알림 목록 조회가 완료되었습니다.",
  "data": {
    "unreadCount": 2,
    "items": [
      {
        "notificationId": 101,
        "type": "SCORE_CHANGE",
        "title": "점수 변화",
        "message": "캠핑 랜턴의 종합 점수가 67점에서 74점으로 올랐어요.",
        "isRead": false,
        "createdAt": "2026-11-13T13:40:00",
        "targetType": "FAVORITE",
        "targetId": 501
      },
      {
        "notificationId": 102,
        "type": "SUPPLY_PRICE",
        "title": "공급가 변동",
        "message": "스마트폰 스트랩 공급가가 120원 내려갔어요.",
        "isRead": false,
        "createdAt": "2026-11-13T12:40:00",
        "targetType": "FAVORITE",
        "targetId": 502
      },
      {
        "notificationId": 103,
        "type": "SYSTEM",
        "title": "무료 리포트 소진",
        "message": "이번 달 무료 정밀분석 횟수를 모두 사용했어요.",
        "isRead": true,
        "createdAt": "2026-11-12T11:40:00",
        "targetType": null,
        "targetId": null
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 3,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `unreadCount` | Number | 읽지 않은 전체 알림 개수 |
| `items` | Array | 알림 목록 |
| `items[].notificationId` | Number | 알림 고유 ID |
| `items[].type` | String | 알림 유형 |
| `items[].title` | String | 알림 제목 |
| `items[].message` | String | 알림 내용 |
| `items[].isRead` | Boolean | 읽음 여부 |
| `items[].createdAt` | DateTime | 알림 발생 시각 |
| `items[].targetType` | String/null | 연결된 대상 유형. 관심상품 알림은 `FAVORITE`, 연결 대상이 없으면 `null` |
| `items[].targetId` | Number/null | 연결된 관심상품의 `favoriteId`. 연결 대상이 없으면 `null` |
| `pagination` | Object | 페이지 정보 |
| `pagination.page` | Number | 현재 페이지 |
| `pagination.size` | Number | 페이지당 조회 개수 |
| `pagination.totalElements` | Number | 전체 조회 대상 알림 개수 |
| `pagination.totalPages` | Number | 전체 페이지 수 |
| `pagination.hasNext` | Boolean | 다음 페이지 존재 여부 |

### 알림 `Type`

| 값 | 설명 |
| --- | --- |
| `SCORE_CHANGE` | 점수 변화 |
| `SUPPLY_PRICE` | 공급가 변동 |
| `STOCK_OUT` | 품절 |
| `STOCK_RESTOCK` | 재입고 |
| `SEARCH_VOLUME` | 검색량 급상승 |
| `SYSTEM` | 시스템 알림 |
| `NOTICE` | 공지 |