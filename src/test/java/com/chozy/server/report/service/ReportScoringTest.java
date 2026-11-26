package com.chozy.server.report.service;

import static com.chozy.server.report.dto.ReportSummary.InvestmentGrade.CONDITIONAL;
import static com.chozy.server.report.dto.ReportSummary.InvestmentGrade.NOT_RECOMMENDED;
import static com.chozy.server.report.dto.ReportSummary.InvestmentGrade.SUITABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReportScoringTest {
  @Test
  void recalculatesWithAvailableSignalsAndAppliesSaleRestriction() {
    var draft = new ObjectMapper().createObjectNode();
    draft.putObject("summary").put("marketConditionScore", 20);
    draft.putObject("externalDiagnosis").put("trendScore", 90).put("competitionScore", 70);
    draft.putObject("internalDiagnosis").putObject("supplierReliability").put("score", 0);

    ReportScoring.recalculateScore(draft, BigDecimal.valueOf(70), BigDecimal.valueOf(20),
        BigDecimal.valueOf(10000), BigDecimal.valueOf(12), true, true);

    assertThat(draft.at("/summary/supplyConditionScore").asInt()).isEqualTo(70);
    assertThat(draft.at("/summary/marketConditionScore").asInt()).isEqualTo(80);
    assertThat(draft.at("/summary/score").asInt()).isEqualTo(76);

    draft.putObject("product").put("saleBlocked", true);
    ReportScoring.recalculateScore(draft, BigDecimal.valueOf(70), BigDecimal.valueOf(20),
        BigDecimal.valueOf(10000), BigDecimal.valueOf(12), true, false);

    assertThat(draft.at("/summary/score").asInt()).isEqualTo(30);
    assertThat(draft.at("/summary/scoreLimitReasons").size()).isEqualTo(1);
  }

  @Test
  void cpcAdjustmentRequiresStrictlyExceedingEachThreshold() {
    assertThat(gradeAt("30.075").grade()).isEqualTo(SUITABLE);
    assertThat(gradeAt("30.076").grade()).isEqualTo(CONDITIONAL);
    assertThat(gradeAt("50.125").grade()).isEqualTo(CONDITIONAL);
    assertThat(gradeAt("50.126").grade()).isEqualTo(NOT_RECOMMENDED);
  }

  private static ReportScoring.Adjustment gradeAt(String cpc) {
    return ReportScoring.grade(BigDecimal.valueOf(80), null, new BigDecimal("100.25"), new BigDecimal(cpc),
        null, null, BigDecimal.valueOf(20), false);
  }
}
