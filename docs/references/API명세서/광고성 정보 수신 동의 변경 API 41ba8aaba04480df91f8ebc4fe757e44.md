# 광고성 정보 수신 동의 변경 API

API Path: /my/marketing-consent
HTTP 메서드: PATCH
도메인: 마이 화면
상태: 시작 전

### 광고성 정보 수신 동의 변경 API

> 로그인한 사용자의 광고성 정보 수신 동의 여부를 변경합니다. 서비스 이용에 필요한 알림 수신 설정과는 별도로 관리합니다.
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
| `agreed` | Boolean | O | 광고성 정보 수신 동의 여부. `true`: 동의, `false`: 철회 |

```json
{
  "agreed": true
}
```

## Response Form Example

### Success

```json
{
  "status": 200,
  "message": "광고성 정보 수신 동의 설정이 변경되었습니다.",
  "data": {
    "agreed": true,
    "updatedAt": "2026-11-17T15:50:00+09:00"
  }
}
```

## Response Data

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `agreed` | Boolean | 변경 후 광고성 정보 수신 동의 상태 |
| `updatedAt` | DateTime/null | 마지막 동의 상태 변경 시각 |

광고성 정보 수신 동의는 선택 항목이며, 동의하지 않아도 서비스의 기본 기능을 이용할 수 있습니다.

동의 상태는 사용자별로 저장하고, 마지막 변경 시각을 기록합니다. 감사 및 이력 관리가 가능하도록 동의·철회 이력을 서버에 별도로 보관합니다. 구체적인 보관 기간은 개인정보 처리 정책에 따릅니다.

동일한 동의 상태로 반복 요청하면 기존 상태를 유지하고 정상 응답합니다. 실제 상태가 변경되지 않은 경우 마지막 변경 시각도 유지합니다.

광고성 정보 수신 동의는 관심상품 변동 알림, 품절·재입고 알림, 정밀분석 이용 안내 등 서비스 운영 알림의 수신 설정과 구분합니다.

이메일을 통한 상품 변동 알림은 `PATCH /my/notification-settings`의 `emailEnabled`로 설정합니다. 광고성 정보 수신 동의를 철회하더라도 사용자가 별도로 활성화한 서비스 알림 설정은 변경하지 않습니다.

현재 광고성 정보 수신 동의 상태는 `GET /my/dashboard`의 `marketingConsent`에서 확인할 수 있으므로 별도 조회 API는 추가하지 않습니다.

회원가입 API에 광고성 정보 수신 동의 필드가 없는 현재 MVP 명세를 기준으로, 별도 동의가 없는 회원의 기본값은 `false`로 처리합니다.