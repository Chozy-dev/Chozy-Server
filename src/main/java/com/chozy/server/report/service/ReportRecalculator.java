package com.chozy.server.report.service;

import static com.chozy.server.report.service.ReportSnapshotReader.decimal;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.report.dto.CoupangCategories.CoupangCategory;
import com.chozy.server.report.repository.ReportRepository.StoredReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReportRecalculator {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal FIVE = BigDecimal.valueOf(5);
  private static final String CATEGORY_NOTICE = "중분류에 따라 실제 수수료율이 달라질 수 있어요.";
  private ReportRecalculator() {}

  static void apply(ObjectNode draft, StoredReport stored, BigDecimal expectedPrice, CoupangCategory category,
      BigDecimal cpc, boolean recalculate, boolean marketChanged) {
    boolean manual = stored.source() == AnalysisSource.MANUAL;
    var profit = draft.path("internalDiagnosis").path("profitability");
    var wholesale = manual ? stored.wholesalePrice() : decimal(profit.path("wholesalePrice"));
    var shipping = manual ? stored.shippingFee() : decimal(profit.path("shippingFee"));
    var minimum = manual ? stored.minimumSellingPrice() : decimal(profit.path("minimumSellingPrice"));
    apply(draft, expectedPrice, category, cpc, recalculate, marketChanged, manual, wholesale, shipping, minimum);
  }

  public static void initializeManual(ObjectNode draft, ManualAnalysisInput input) {
    apply(draft, null, null, null, true, true, true,
        input.wholesalePrice(), input.shippingFee(), input.minimumSellingPrice());
  }

  private static void apply(ObjectNode draft, BigDecimal expectedPrice, CoupangCategory category, BigDecimal cpc,
      boolean recalculate, boolean marketChanged, boolean manual, BigDecimal wholesale, BigDecimal shipping,
      BigDecimal minimum) {
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
      var profitabilityScore = marginRate == null ? null : ReportScoring.profitabilityScore(marginRate);
      profit.put("score", profitabilityScore);
      ReportScoring.recalculateScore(draft, profitabilityScore, marginRate, reference, feeRate, manual, marketChanged);
      updateStrategy(strategy, reference, market, priceBasis, costs);
      updateSimulation(simulation, strategy, costs, reference, minimum, margin, marginRate);
    }

    var score = decimal(summary.path("score"));
    String scoreStatus = summary.path("scoreStatus").asText(score == null ? "INSUFFICIENT_DATA" : "CALCULATED");
    if (score != null && "CALCULATED".equals(scoreStatus)) {
      var adjustment = ReportScoring.grade(score, costs.profitAt(minimum), margin, cpc, minimum, market, marginRate,
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

  private static BigDecimal first(BigDecimal... values) {
    for (var value : values) {
      if (value != null) return value;
    }
    return null;
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
}
