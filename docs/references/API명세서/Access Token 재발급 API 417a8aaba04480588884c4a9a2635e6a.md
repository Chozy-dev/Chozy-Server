# Access Token 재발급 API

API Path: /auth/reissue 
HTTP 메서드: POST
도메인: 회원인증
상태: 시작 전

### Access Token 재발급 API

> Refresh Token을 검증하고 새로운 Access Token과 Refresh Token을 발급합니다.
> 

### Authentication

불필요

## Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `refreshToken` | String | O | 로그인 시 발급받은 Refresh Token |

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
  "message": "토큰 재발급이 완료되었습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.new-access.signature",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.new-refresh.signature",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | String | 새로 발급한 JWT Access Token |
| `refreshToken` | String | 새로 발급한 JWT Refresh Token |
| `tokenType` | String | 인증 방식 (`Bearer`) |
| `expiresIn` | Number | Access Token 유효시간(초) |