package com.chozy.server.report.service;

import static com.chozy.server.report.dto.ReportSummary.InvestmentGrade.*;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.report.dto.CoupangCategories.CoupangCategory;
import com.chozy.server.report.dto.ReportSummary.InvestmentGrade;
import com.chozy.server.report.repository.ReportRepository.StoredReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

final class ReportRecalculator {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal FIVE = BigDecimal.valueOf(5);
  private static final String CATEGORY_NOTICE = "중분류에 따라 실제 수수료율이 달라질 수 있어요.";
  private static final String[] MARKET_COMPONENTS = {"trendScore", "competitionScore", "seasonalityScore"};
  private static final int[] MARGIN_THRESHOLDS = {30, 25, 20, 15, 10, 5};
  private static final int[] PROFITABILITY_SCORES = {100, 85, 70, 55, 40, 25};

  private ReportRecalculator() {}

  static void apply(ObjectNode draft, StoredReport stored, BigDecimal expectedPrice, CoupangCategory category,
      BigDecimal cpc, boolean recalculate, boolean marketChanged) {
    var profit = draft.withObjectProperty("internalDiagnosis").withObjectProperty("profitability");
    var simulation = draft.withObjectProperty("investmentSimulation");
    var strategy = draft.withObjectProperty("investmentStrategy");
    var registration = strategy.withObjectProperty("coupangRegistration");
    var summary = draft.withObjectProperty("summary");
    var external = draft.withObjectProperty("externalDiagnosis");
    simulation.put("cpc", cpc);

    if (category != null) {
      registration.put("categoryCode", category.categoryCode()).put("categorySource", "USER_SELECTED")
          .put("feeRate", category.feeRate()).put("feeRule", "대분류 기본 요율에 부가세를 포함했어요.")
          .put("notice", CATEGORY_NOTICE);
      registration.putArray("categoryPath").add(category.categoryName());
      registration.putArray("requiredCertifications");
      registration.putArray("productInformationNotices");
      profit.put("feeRate", category.feeRate());
    }

    boolean manual = stored.source() == AnalysisSource.MANUAL;
    var wholesale = manual ? stored.wholesalePrice() : decimal(profit.path("wholesalePrice"));
    var shipping = manual ? stored.shippingFee() : decimal(profit.path("shippingFee"));
    var minimum = manual ? stored.minimumSellingPrice() : decimal(profit.path("minimumSellingPrice"));
    var market = decimal(profit.path("marketPrice"));
    var legacyReference = profit.has("marketPrice") ? null : decimal(profit.path("referenceSellingPrice"));
    var reference = first(expectedPrice, market, legacyReference);
    var legacyFee = profit.has("feeRate") || registration.has("feeRate") ? null : decimal(simulation.path("feeRate"));
    var feeRate = first(decimal(profit.path("feeRate")), decimal(registration.path("feeRate")), legacyFee);
    var costs = new Costs(wholesale, shipping, feeRate);
    var margin = costs.profitAt(reference);
    if (!recalculate) margin = first(margin, decimal(profit.path("profitPerUnit")));
    var marginRate = first(percent(margin, reference), recalculate ? null : decimal(profit.path("marginRate")));

    if (recalculate) {
      String priceBasis = reference == null ? null : expectedPrice == null ? "MARKET" : "USER_INPUT";
      profit.put("expectedSellingPrice", expectedPrice).put("referenceSellingPrice", reference)
          .put("priceBasis", priceBasis).put("wholesalePriceRatio", percent(wholesale, reference))
          .put("feeRate", feeRate).put("feeAmount", costs.feeAt(reference))
          .put("profitPerUnit", margin).put("marginRate", marginRate);
      var profitabilityScore = marginRate == null ? null : profitabilityScore(marginRate);
      profit.put("score", profitabilityScore);
      recalculateScore(draft, profitabilityScore, marginRate, reference, feeRate, manual, marketChanged);
      updateStrategy(strategy, reference, market, priceBasis, costs);
      updateSimulation(simulation, strategy, costs, reference, minimum, margin, marginRate);
    }

    var score = decimal(summary.path("score"));
    String scoreStatus = summary.path("scoreStatus").asText(score == null ? "INSUFFICIENT_DATA" : "CALCULATED");
    if (score != null && "CALCULATED".equals(scoreStatus)) {
      var adjustment = grade(score, costs.profitAt(minimum), margin, cpc, minimum, market, marginRate,
          "HIGH".equals(external.path("competitionLevel").asText()));
      summary.put("investmentGrade", adjustment.grade().name()).put("gradeReason", adjustment.reason());
      updateCpcWarning(simulation, adjustment.reason());
    } else {
      updateCpcWarning(simulation, null);
    }
  }

  private static void updateCpcWarning(ObjectNode simulation, String gradeReason) {
    var previous = simulation.path("warnings");
    var warnings = simulation.putArray("warnings");
    for (var warning : previous) {
      if (!warning.asText().startsWith("CPC")) warnings.add(warning);
    }
    if (gradeReason != null && gradeReason.startsWith("CPC")) warnings.add(gradeReason);
  }

  private static void recalculateScore(ObjectNode draft, BigDecimal profitability, BigDecimal marginRate,
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

  private static void updateStrategy(ObjectNode strategy, BigDecimal reference, BigDecimal market, String priceBasis,
      Costs costs) {
    var comparison = strategy.withObjectProperty("priceCompetitiveness");
    comparison.put("priceBasis", priceBasis).put("referencePrice", reference).put("marketPrice", market)
        .put("sellingPrice", reference).put("sellingPriceDifferenceRate", difference(reference, market))
        .put("rocketPriceDifferenceRate", difference(reference, decimal(comparison.path("rocketMedianPrice"))));
    for (JsonNode platform : strategy.path("platformComparison")) {
      if (!(platform instanceof ObjectNode item)) continue;
      var platformCosts = "COUPANG".equals(item.path("platform").asText()) ? costs
          : new Costs(costs.wholesale(), costs.shipping(), decimal(item.path("feeRate")));
      var margin = platformCosts.profitAt(reference);
      item.put("feeRate", platformCosts.feeRate()).put("priceBasis", priceBasis)
          .put("profitPerUnit", margin).put("marginRate", percent(margin, reference));
    }
  }

  private static void updateSimulation(ObjectNode simulation, ObjectNode strategy, Costs costs, BigDecimal reference,
      BigDecimal minimum, BigDecimal margin, BigDecimal marginRate) {
    var breakEven = costs.breakEven(BigDecimal.ZERO);
    var target = marginRate == null ? null : marginRate.max(FIVE);
    var recommended = roundUpToHundred(costs.breakEven(target));
    if (recommended != null) {
      if (minimum != null) recommended = recommended.max(minimum);
    }
    var marketMax = decimal(strategy.at("/priceCompetitiveness/marketMaxPrice"));
    simulation.put("wholesalePrice", costs.wholesale()).put("shippingFee", costs.shipping()).put("feeRate", costs.feeRate())
        .put("sellingPrice", reference).put("minimumSellingPrice", minimum).put("targetMarginRate", target)
        .put("recommendedSellingPrice", recommended).put("recommendedPriceMarginRate", percent(costs.profitAt(recommended), recommended))
        .put("sellingPriceMin", roundUpToHundred(breakEven)).put("sellingPriceMax", marketMax)
        .put("pricePolicyViolated", reference == null || minimum == null ? null : reference.compareTo(minimum) < 0)
        .put("feeAmount", costs.feeAt(reference)).put("profitPerUnit", margin).put("marginRate", marginRate)
        .put("breakEvenSellingPrice", breakEven).put("breakEvenAdCostPerOrder", positive(margin) ? margin : null)
        .put("breakEvenRoas", percent(reference, margin));
  }

  private static BigDecimal roundUpToHundred(BigDecimal price) {
    return price == null ? null : price.divide(HUNDRED, 0, RoundingMode.CEILING).multiply(HUNDRED);
  }

  private static Adjustment grade(BigDecimal score, BigDecimal minimumMargin, BigDecimal margin, BigDecimal cpc,
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
    return cpc != null && positive(margin) && cpc.compareTo(margin.multiply(new BigDecimal(ratio))) > 0;
  }

  private static BigDecimal profitabilityScore(BigDecimal marginRate) {
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

  private static BigDecimal difference(BigDecimal value, BigDecimal base) {
    return value == null || base == null ? null : percent(value.subtract(base), base);
  }

  private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
    return numerator == null || !positive(denominator) ? null
        : numerator.multiply(HUNDRED).divide(denominator, 6, RoundingMode.HALF_UP);
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private static boolean below(BigDecimal value, int threshold) {
    return value != null && value.compareTo(BigDecimal.valueOf(threshold)) < 0;
  }

  private static BigDecimal first(BigDecimal... values) {
    for (var value : values) {
      if (value != null) return value;
    }
    return null;
  }

  static BigDecimal decimal(JsonNode value) {
    return value.isNumber() ? value.decimalValue() : null;
  }

  private record Costs(BigDecimal wholesale, BigDecimal shipping, BigDecimal feeRate) {
    BigDecimal feeAt(BigDecimal price) {
      return price == null || feeRate == null ? null : price.multiply(feeRate).movePointLeft(2);
    }

    BigDecimal profitAt(BigDecimal price) {
      var fee = feeAt(price);
      return fee == null || wholesale == null || shipping == null ? null : price.subtract(wholesale).subtract(shipping).subtract(fee);
    }

    BigDecimal breakEven(BigDecimal target) {
      if (target == null || feeRate == null || wholesale == null || shipping == null) return null;
      var retention = HUNDRED.subtract(feeRate).subtract(target);
      return positive(retention) ? wholesale.add(shipping).multiply(HUNDRED).divide(retention, 0, RoundingMode.CEILING) : null;
    }
  }

  private record Adjustment(InvestmentGrade grade, String reason) {}
}
