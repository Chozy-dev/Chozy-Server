package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record ReportSummary(
    ScoreStatus scoreStatus,
    @Schema(types = {"number", "null"}) BigDecimal score,
    InvestmentGrade investmentGrade,
    @Schema(types = {"string", "null"}) String gradeReason,
    @Schema(types = {"number", "null"}) BigDecimal supplyConditionScore,
    @Schema(types = {"number", "null"}) BigDecimal marketConditionScore,
    String description,
    List<String> recommendationReasons,
    List<String> concerns,
    List<String> scoreLimitReasons,
    List<String> requiredInputs) {
  public ReportSummary {
    score = withinRange(score);
    supplyConditionScore = withinRange(supplyConditionScore);
    marketConditionScore = withinRange(marketConditionScore);
    if (scoreStatus == null) {
      scoreStatus = score == null ? ScoreStatus.INSUFFICIENT_DATA : ScoreStatus.CALCULATED;
    }
    if (scoreStatus == ScoreStatus.CALCULATED && score == null) {
      scoreStatus = ScoreStatus.INSUFFICIENT_DATA;
    }
    if (scoreStatus != ScoreStatus.CALCULATED) {
      score = null;
      investmentGrade = null;
      gradeReason = null;
    }
    recommendationReasons = recommendationReasons == null ? List.of() : List.copyOf(recommendationReasons);
    concerns = concerns == null ? List.of() : List.copyOf(concerns);
    scoreLimitReasons = scoreLimitReasons == null ? List.of() : List.copyOf(scoreLimitReasons);
    requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
  }

  private static BigDecimal withinRange(BigDecimal value) {
    return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0 ? value : null;
  }

  public enum ScoreStatus { CALCULATED, WAITING_FOR_INPUT, INSUFFICIENT_DATA }

  public enum InvestmentGrade { SUITABLE, CONDITIONAL, NOT_RECOMMENDED }
}
