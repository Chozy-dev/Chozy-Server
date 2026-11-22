# 마이페이지 정보 및 이용 내역 조회 API

API Path: /my/dashboard
HTTP 메서드: GET
도메인: 마이 화면
상태: 시작 전

### 마이페이지 정보 및 이용 내역 조회 API

> 로그인한 사용자의 프로필, 현재 요금제, 이번 달 정밀분석 및 키워드 스크리닝 이용 내역, 관심상품 사용량과 광고성 정보 수신 동의 상태를 조회합니다.
> 

### Authentication

필수(JWT)

## Request Header

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `Authorization` | String | O | `Bearer {accessToken}` |

## Request Query Parameters

없음

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "마이페이지 정보 조회가 완료되었습니다.",
  "data": {
    "profile": {
      "userId": 1024,
      "loginId": "chozy123",
      "nickname": "초보셀러",
      "email": "chozy123@example.com",
      "createdAt": "2026-11-14T04:00:00+09:00",
      "membershipDays": 4
    },
    "plan": {
      "code": "FREE",
      "name": "무료 플랜"
    },
    "reportUsage": {
      "period": "2026-11",
      "periodType": "MONTH",
      "usedCount": 1,
      "limitCount": 3,
      "remainingCount": 2,
      "nextResetAt": "2026-12-01T00:00:00+09:00"
    },
    "screeningUsage": {
      "period": "2026-11",
      "usedCount": 12,
      "unlimited": true
    },
    "favoriteUsage": {
      "usedCount": 3,
      "limitCount": 10
    },
    "marketingConsent": {
      "agreed": false,
      "updatedAt": null
    }
  }
}
```

## Response Data

### 프로필 (`profile`)

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `profile` | Object | 사용자 프로필 |
| `profile.userId` | Number | 회원 고유 ID |
| `profile.loginId` | String | 로그인 아이디 |
| `profile.nickname` | String | 사용자 닉네임 |
| `profile.email` | String | 이메일 주소 |
| `profile.createdAt` | DateTime | 가입 시각 |
| `profile.membershipDays` | Number | 서비스 가입 후 경과 일수. 가입 당일을 1일째로 계산 |

### 요금제 (`plan`)

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `plan` | Object | 현재 이용 요금제 |
| `plan.code` | String | 플랜 코드. MVP에서는 `FREE` |
| `plan.name` | String | 화면에 표시할 요금제 이름 |

### 정밀분석 이용 내역 (`reportUsage`)

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `reportUsage` | Object | 이번 달 정밀분석 사용량 |
| `reportUsage.period` | String | 사용량 집계 월 (`YYYY-MM`) |
| `reportUsage.periodType` | String | 집계 기간 종류. `MONTH` |
| `reportUsage.usedCount` | Number | 이번 달 사용한 정밀분석 횟수 |
| `reportUsage.limitCount` | Number | 이번 달 기본 제공 횟수 |
| `reportUsage.remainingCount` | Number | 남은 정밀분석 횟수 |
| `reportUsage.nextResetAt` | DateTime | 다음 사용량 초기화 시각 |

### 기타 이용 내역

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `screeningUsage` | Object | 이번 달 키워드 스크리닝 이용 내역 |
| `screeningUsage.period` | String | 집계 월 (`YYYY-MM`) |
| `screeningUsage.usedCount` | Number | 이번 달 키워드 스크리닝 실행 횟수 |
| `screeningUsage.unlimited` | Boolean | 이용 횟수 제한 없음 여부. MVP에서는 true |
| `favoriteUsage` | Object | 관심상품 사용량 |
| `favoriteUsage.usedCount` | Number | 현재 등록된 전체 관심상품 개수 |
| `favoriteUsage.limitCount` | Number | 등록 가능한 최대 관심상품 개수 |
| `marketingConsent` | Object | 광고성 정보 수신 동의 상태 |
| `marketingConsent.agreed` | Boolean | 광고성 정보 수신 동의 여부 |
| `marketingConsent.updatedAt` | DateTime/null | 마지막 동의 상태 변경 시각 |

무료 플랜은 매달 정밀분석 3회를 제공하며, 한국 시간 기준 매월 1일 0시에 초기화됩니다. 횟수는 실제 리포트 생성에 성공한 경우에만 차감합니다.

`reportUsage`는 기존 `GET /home/report-usage` API와 동일한 사용량 데이터를 사용합니다. 마이페이지에서 별도의 카운터를 관리하지 않습니다.

키워드 스크리닝은 횟수 제한이 없으며, `usedCount`는 로그인 회원이 해당 월에 실행한 스크리닝 요청 횟수입니다. 기존 기록 재조회는 횟수에 포함하지 않습니다.

관심상품 개수는 관심중·판매중을 합산합니다. 무료 플랜의 등록 한도는 10개이며, 프리미엄 플랜은 MVP에서 제공하지 않습니다.

`membershipDays`는 서버에서 한국 날짜 기준으로 계산합니다. 

"이번 달 한 번에 확인한 것: 공급 이력 · 검색 추이 · 광고 경쟁 · 예상 마진" 안내와 요금제 소개 내용은 정적 UI 텍스트로 처리합니다. 별도 API 데이터가 필요하지 않습니다.

현재 광고성 정보 수신 동의 상태는 `marketingConsent`로 반환하므로, 설정 화면에서 별도 조회 API를 추가하지 않아도 됩니다.