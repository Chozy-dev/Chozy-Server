package com.chozy.server.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record NaverCoupangComparison(
    @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime updatedAt,
    @Schema(types = {"integer", "null"}) Integer themeCode,
    @Schema(types = {"string", "null"}) String themeName,
    List<ComparedKeyword> matchedKeywords,
    List<ComparedKeyword> unmatchedKeywords,
    String notice) {
  public record ComparedKeyword(String keyword, long monthlySearchCount,
      @Schema(types = {"number", "null"}) BigDecimal growthRate, long matchedProductCount) {}
}
