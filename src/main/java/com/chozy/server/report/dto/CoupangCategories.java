package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record CoupangCategories(List<CoupangCategory> categories) {
  public record CoupangCategory(
      @Schema(description = "Chozy 내부 대분류 식별 코드", example = "ELECTRONICS") String categoryCode,
      @Schema(example = "가전디지털") String categoryName,
      @Schema(description = "부가세 10%를 포함한 기본 판매 수수료율(%)", example = "8.58") BigDecimal feeRate) {}
}
