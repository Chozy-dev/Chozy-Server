package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record ExternalDiagnosis(
    @Schema(types = {"string", "null"}) String keyword,
    String originalKeyword,
    @Schema(types = {"string", "null"}) String keywordChangeReason,
    @Schema(types = {"string", "null"}) String broadKeyword,
    @Schema(types = {"number", "null"}) BigDecimal broadKeywordMonthlySearchCount,
    @Schema(types = {"number", "null"}) BigDecimal monthlySearchCount,
    boolean isSearchCountEstimated,
    @Schema(types = {"number", "null"}) BigDecimal lastMonthGrowthRate,
    @Schema(types = {"string", "null"}) String competitionLevel,
    @Schema(types = {"number", "null"}) BigDecimal averageAdDepth,
    @Schema(types = {"number", "null"}) BigDecimal trendScore,
    @Schema(types = {"number", "null"}) BigDecimal competitionScore,
    @Schema(types = {"number", "null"}) BigDecimal seasonalityScore,
    @Schema(types = {"number", "null"}) BigDecimal recent4WeeksGrowthRate,
    @Schema(types = {"number", "null"}) BigDecimal comparisonProductCount,
    @Schema(types = {"number", "null"}) BigDecimal rocketProductCount,
    @Schema(types = {"number", "null"}) BigDecimal rocketDeliveryRatio,
    Seasonality seasonality,
    SearchTrend searchTrend,
    @Schema(types = {"string", "null"}) String notice) {

  public record Seasonality(
      String type,
      List<Integer> peakMonths,
      @Schema(types = {"number", "null"}) BigDecimal weeksUntilPeak,
      @Schema(types = {"number", "null"}) BigDecimal weeksRemainingInPeak,
      String description) {
    public Seasonality {
      peakMonths = peakMonths == null ? List.of() : List.copyOf(peakMonths);
    }
  }

  public record SearchTrend(
      List<DailyTrend> last30Days,
      List<MonthlyTrend> last12Months,
      List<SeasonalTrend> seasonalPattern) {
    public SearchTrend {
      last30Days = last30Days == null ? List.of() : List.copyOf(last30Days);
      last12Months = last12Months == null ? List.of() : List.copyOf(last12Months);
      seasonalPattern = seasonalPattern == null ? List.of() : List.copyOf(seasonalPattern);
    }

    public record DailyTrend(
        String date,
        BigDecimal value) {
    }

    public record MonthlyTrend(
        String month,
        BigDecimal value) {
    }

    public record SeasonalTrend(
        BigDecimal month,
        BigDecimal value) {
    }
  }
}
