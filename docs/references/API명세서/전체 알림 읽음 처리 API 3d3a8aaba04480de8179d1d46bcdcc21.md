# 전체 알림 읽음 처리 API

API Path: /home/notifications/read-all
HTTP 메서드: PATCH
도메인: 홈 화면
상태: 시작 전

### **전체 알림 읽음 처리 API**

> 사용자의 모든 읽지 않은 알림을 읽음 상태로 일괄 변경합니다. 알림 화면 우측 상단의 `모두 읽음 처리` 버튼을 클릭하면 호출합니다.
> 

### Authentication

필수 (JWT)

## **Request Query Parameters**

요청 파라미터 없음

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "모든 알림을 읽음 처리했습니다.",
  "data": {
    "updatedCount": 2,
    "unreadCount": 0
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `updatedCount` | Number | 읽음 처리된 알림 개수 |
| `unreadCount` | Number | 처리 후 읽지 않은 알림 개수 |