package com.chozy.server.common.response;

public record ApiResponse<T>(int status, String message, T data) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(200, "요청이 완료되었습니다.", data);
  }

  public static <T> ApiResponse<T> success(int status, String message, T data) {
    if (status < 200 || status >= 300)
      throw new IllegalArgumentException("Success status required");
    return new ApiResponse<>(status, message, data);
  }
}
