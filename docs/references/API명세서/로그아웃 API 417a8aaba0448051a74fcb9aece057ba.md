# 로그아웃 API

API Path: /auth/logout
HTTP 메서드: POST
도메인: 회원인증
상태: 시작 전

### 로그아웃 API

> 로그인한 사용자의 Refresh Token을 무효화하여 토큰 재발급을 차단합니다.
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
| `refreshToken` | String | O | 무효화할 Refresh Token |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.refresh.signature"
}
```

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "로그아웃이 완료되었습니다.",
  "data": null
}
```

## Response Data

없음