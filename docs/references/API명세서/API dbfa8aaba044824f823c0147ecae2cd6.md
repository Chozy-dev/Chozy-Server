# API

상태: 시작 전

### **000API**

> 
> 

## **Request Body**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
|  |  |  |  |

## Response Form Example

### Success

```json
{
  "message": "인증번호가 발송되었습니다."
}
```

### Error

- 인증번호 발송 실패

```json
{
  "message": "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.",
  "status": 500,
  "errors": [],
  "code": "A007"
}
```