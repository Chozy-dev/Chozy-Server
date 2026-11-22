package com.chozy.server.common.error;

import java.util.List;

/** Item schema for errors is unspecified; do not expose rejected values or exception text. */
public record ErrorResponse(String message, int status, List<Void> errors, String code) {
  public static ErrorResponse of(ErrorCode code) {
    return new ErrorResponse(code.message(), code.status(), List.of(), code.code());
  }
}
