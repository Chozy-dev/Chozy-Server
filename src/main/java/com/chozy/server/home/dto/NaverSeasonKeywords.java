package com.chozy.server.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record NaverSeasonKeywords(
    @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime updatedAt,
    List<SeasonTheme> themes,
    @Schema(types = {"integer", "null"}) Integer selectedThemeCode,
    List<SeasonKeyword> items) {
  public record SeasonTheme(int themeCode, String themeName) {}

  public record SeasonKeyword(int rank, String keyword, long monthlySearchCount,
      @Schema(types = {"number", "null"}) BigDecimal growthRate) {}
}
