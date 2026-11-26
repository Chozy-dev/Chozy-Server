package com.chozy.server.report.service;

import static com.chozy.server.report.dto.ReportSummary.InvestmentGrade.*;
import static com.chozy.server.report.service.ReportSnapshotReader.decimal;

import com.chozy.server.report.dto.ReportSummary.InvestmentGrade;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

final class ReportScoring {
  private static final String[] MARKET_COMPONENTS = {"trendScore", "competitionScore", "seasonalityScore"};
  private static final int[] MARGIN_THRESHOLDS = {30, 25, 20, 15, 10, 5};
  private static final int[] PROFITABILITY_SCORES = {100, 85, 70, 55, 40, 25};

  private ReportScoring() {}

  static void recalculateScore(ObjectNode draft, BigDecimal profitability, BigDecimal marginRate,
      BigDecimal reference, BigDecimal feeRate, boolean manual, boolean marketChanged) {
    var summary = draft.withObjectProperty("summary");
    var supply = new ArrayList<BigDecimal>();
    var internal = draft.path("internalDiagnosis");
    supply.add(profitability);
    supply.add(manual ? null : decimal(internal.at("/supplyStability/score")));
    supply.add(manual ? null : decimal(internal.at("/supplierReliability/score")));
    var supplyScore = weighted(supply, 45, 30, 25);
    var marketScore = marketChanged ? null : decimal(summary.path("marketConditionScore"));
    if (marketScore == null) {
      var external = draft.path("externalDiagnosis");
      var components = new ArrayList<BigDecimal>();
      for (String component : MARKET_COMPONENTS) components.add(decimal(external.path(component)));
      marketScore = weighted(components, 1, 1, 1);
    }
    summary.put("supplyConditionScore", supplyScore).put("marketConditionScore", marketScore);
    var required = summary.putArray("requiredInputs");
    if (reference == null) required.add("EXPECTED_SELLING_PRICE");
    if (feeRate == null) required.add("COUPANG_CATEGORY");
    if (!required.isEmpty() || profitability == null) {
      summary.put("scoreStatus", required.isEmpty() ? "INSUFFICIENT_DATA" : "WAITING_FOR_INPUT");
      summary.putNull("score").putNull("investmentGrade").putNull("gradeReason");
      summary.putArray("scoreLimitReasons");
      return;
    }
    var scores = new ArrayList<BigDecimal>();
    scores.add(supplyScore);
    scores.add(marketScore);
    var score = weighted(scores, 40, 60).setScale(0, RoundingMode.HALF_UP);
    var limits = summary.putArray("scoreLimitReasons");
    if (draft.at("/product/saleBlocked").asBoolean()) {
      score = score.min(BigDecimal.valueOf(30));
      limits.add("판매가 제한된 상품이라 종합점수를 30점 이하로 제한했어요.");
    }
    if (below(marginRate, 10)) {
      score = score.min(BigDecimal.valueOf(60));
      limits.add("기준 마진율이 10% 미만이라 종합점수를 60점 이하로 제한했어요.");
    }
    summary.put("scoreStatus", "CALCULATED").put("score", score);
  }

  static Adjustment grade(BigDecimal score, BigDecimal minimumMargin, BigDecimal margin, BigDecimal cpc,
      BigDecimal minimum, BigDecimal market, BigDecimal marginRate, boolean highCompetition) {
    var base = below(score, 50) ? NOT_RECOMMENDED : below(score, 75) ? CONDITIONAL : SUITABLE;
    if (minimumMargin != null && minimumMargin.signum() <= 0)
      return new Adjustment(NOT_RECOMMENDED, "판매가 하한으로 판매해도 개당 마진이 0원 이하예요.");
    if (margin != null && margin.signum() <= 0)
      return new Adjustment(NOT_RECOMMENDED, "기준 판매가로 판매하면 개당 마진이 0원 이하예요.");
    if (exceeds(cpc, margin, "0.50"))
      return new Adjustment(NOT_RECOMMENDED, "CPC가 개당 마진의 50%를 초과해 투자 비추천으로 조정됐어요.");
    if (base != SUITABLE) return new Adjustment(base, null);
    if (exceeds(cpc, margin, "0.30"))
      return new Adjustment(CONDITIONAL, "CPC가 개당 마진의 30%를 초과해 조건부 적합으로 조정됐어요.");
    if (minimum != null && market != null && minimum.compareTo(market) > 0)
      return new Adjustment(CONDITIONAL, "판매가 하한이 시장가보다 높아 가격 경쟁에서 불리해요.");
    if (below(marginRate, 15))
      return new Adjustment(CONDITIONAL, "기준 마진율이 15% 미만이라 조건부 적합으로 조정됐어요.");
    if (cpc == null && highCompetition)
      return new Adjustment(CONDITIONAL, "광고 경쟁이 높아 조건부 적합으로 조정됐어요.");
    return new Adjustment(base, null);
  }

  private static boolean exceeds(BigDecimal cpc, BigDecimal margin, String ratio) {
    return cpc != null && margin != null && margin.signum() > 0 && cpc.compareTo(margin.multiply(new BigDecimal(ratio))) > 0;
  }

  static BigDecimal profitabilityScore(BigDecimal marginRate) {
    for (int i = 0; i < MARGIN_THRESHOLDS.length; i++) {
      if (!below(marginRate, MARGIN_THRESHOLDS[i])) return BigDecimal.valueOf(PROFITABILITY_SCORES[i]);
    }
    return BigDecimal.TEN;
  }

  private static BigDecimal weighted(ArrayList<BigDecimal> values, int... weights) {
    var sum = BigDecimal.ZERO;
    int totalWeight = 0;
    for (int i = 0; i < weights.length; i++) {
      var value = values.get(i);
      if (value != null) {
        sum = sum.add(value.multiply(BigDecimal.valueOf(weights[i])));
        totalWeight += weights[i];
      }
    }
    return totalWeight == 0 ? null : sum.divide(BigDecimal.valueOf(totalWeight), 6, RoundingMode.HALF_UP);
  }

  private static boolean below(BigDecimal value, int threshold) {
    return value != null && value.compareTo(BigDecimal.valueOf(threshold)) < 0;
  }

  record Adjustment(InvestmentGrade grade, String reason) {}
}
