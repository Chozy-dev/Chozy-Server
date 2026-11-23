package com.chozy.server.common.error;

public enum ErrorCode {
  INVALID_INPUT(400, "C001", "입력하신 정보가 올바르지 않습니다. 다시 확인해 주세요."),
  DUPLICATE(409, "C002", "이미 등록된 정보입니다. 다른 정보로 시도해 주세요."),
  NOT_FOUND(404, "C003", "요청하신 페이지나 기능을 찾을 수 없습니다."),
  INTERNAL_ERROR(500, "C004", "일시적인 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
  TYPE_MISMATCH(400, "C005", "입력 형식이 올바르지 않습니다. 올바른 형식으로 입력해 주세요."),
  FORBIDDEN(403, "C006", "해당 작업에 대한 권한이 없습니다."),
  METHOD_NOT_ALLOWED(405, "C007", "지원하지 않는 요청 방식입니다. 올바른 방식으로 다시 시도해 주세요."),
  MISSING_PARAMETER(400, "C008", "필수 파라미터가 누락되었습니다. 요청을 확인해 주세요."),
  INVALID_JSON(400, "C009", "JSON 형식이 올바르지 않습니다. 요청 데이터를 확인해 주세요."),
  UNSUPPORTED_MEDIA_TYPE(415, "C010", "지원하지 않는 미디어 타입입니다."),
  FILE_TOO_LARGE(400, "C011", "파일 크기가 허용된 범위를 초과했습니다."),
  REQUEST_TIMEOUT(408, "C012", "요청 시간이 초과되었습니다. 다시 시도해 주세요."),
  REPORT_LIMIT_EXHAUSTED(429, "R001", "이번 달 무료 리포트 횟수를 모두 사용했습니다. 다음 달 1일 0시(한국 시간)에 다시 이용할 수 있습니다."),
  REPORT_SLOTS_RESERVED(409, "R002", "진행 중인 정밀분석이 남은 횟수를 사용 중입니다. 완료 후 다시 시도해 주세요."),
  ANALYSIS_UNAVAILABLE(503, "R003", "현재 직접 입력 정밀분석을 사용할 수 없습니다."),
  URL_ANALYSIS_UNAVAILABLE(503, "R003", "현재 상품 URL 분석에 필요한 데이터를 조회할 수 없습니다."),
  REPORT_SCORE_UNAVAILABLE(409, "R004", "종합점수가 계산된 리포트에만 의견을 남길 수 있습니다."),
  REPORT_INPUTS_CONFLICT(409, "R005", "다른 요청으로 리포트가 변경됐어요. 최신 리포트를 조회한 뒤 다시 시도해 주세요."),
  REPORT_RECALCULATION_UNAVAILABLE(503, "R006", "리포트 재계산에 필요한 데이터를 조회할 수 없습니다."),
  FAVORITE_LIMIT_REACHED(409, "F001", "관심상품 등록 한도에 도달했습니다."),
  SCREENING_UNAVAILABLE(503, "S001", "현재 키워드 분석에 필요한 데이터를 조회할 수 없습니다."),
  ACCESS_TOKEN_EXPIRED(401, "A003", "로그인이 만료되었습니다. 다시 로그인해 주세요."),
  INVALID_AUTHENTICATION(401, "A004", "인증 정보가 올바르지 않습니다. 다시 로그인해 주세요."),
  REFRESH_TOKEN_EXPIRED(401, "A005", "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."),
  INVALID_VERIFICATION_CODE(400, "A006", "인증번호가 올바르지 않거나 만료되었습니다. 다시 확인해 주세요.");
  private final int status;
  private final String code;
  private final String message;

  ErrorCode(int status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
