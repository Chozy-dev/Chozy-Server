package com.chozy.server.report.service;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.AnalysisType;
import com.chozy.server.report.dto.ReportDetail;
import com.chozy.server.report.repository.ReportRepository.StoredManualReport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;

/** Projects V6 manual snapshots and their saved inputs onto the report detail contract. */
final class ReportSnapshotReader {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final String MISSING_DATA = "저장된 분석 데이터가 부족합니다.";
  private static final String SUPPLIER_CHECK = "제조사 정보가 없어 공급사에 상표 사용 여부를 확인해 주세요.";
  private static final List<String> MANUAL_UNAVAILABLE = List.of("SUPPLY_STABILITY", "SUPPLIER_RELIABILITY");

  private final ObjectMapper mapper;
  private final ObjectReader details;
  private final ObjectReader trees;

  ReportSnapshotReader(ObjectMapper mapper) {
    this.mapper = mapper;
    this.trees = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    this.details = mapper.readerFor(ReportDetail.class)
        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
  }

  ReportDetail read(StoredManualReport stored) {
    try {
      var report = (ObjectNode) trees.readTree(stored.snapshot());
      report.put("reportId", stored.reportId()).put("analysisId", stored.analysisId())
          .put("analysisType", AnalysisType.MANUAL.name()).put("source", AnalysisSource.MANUAL.name())
          .put("analyzedAt", stored.completedAt().atZoneSameInstant(KOREA).toOffsetDateTime().toString());
      manualProduct(report.withObjectProperty("product"), stored);
      fillInternalDiagnosis(report.withObjectProperty("internalDiagnosis"), stored);
      fillAnalysisSections(report, stored);
      fillSimulation(report.withObjectProperty("investmentSimulation"), stored);
      addUnavailableFeatures(report);
      return details.readValue(report.traverse(mapper));
    } catch (IOException ex) {
      throw new IllegalStateException("Invalid stored report", ex);
    }
  }

  private static void manualProduct(ObjectNode product, StoredManualReport stored) {
    product.put("productName", stored.productName()).put("isAffiliateLink", false).put("saleBlocked", false);
    for (String field : List.of("productId", "productImageUrl", "originalUrl", "resolvedUrl", "purchaseUrl",
        "selectedOption", "manufacturer", "supplierName", "originCountry", "modelName", "sourceCategory",
        "popularProductClassification", "supplyConditions")) {
      product.putNull(field);
    }
  }

  private static void fillInternalDiagnosis(ObjectNode internal, StoredManualReport stored) {
    internal.putNull("supplyStability").putNull("supplierReliability");
    var profit = internal.withObjectProperty("profitability");
    profit.put("wholesalePrice", stored.wholesalePrice()).put("shippingFee", stored.shippingFee())
        .put("shippingPaymentType", "USER_INPUT").put("hasPricePolicy", stored.hasPricePolicy())
        .put("minimumSellingPrice", stored.minimumSellingPrice())
        .put("minimumSellingPriceLabel", stored.hasPricePolicy() ? "최종준수가" : null);
    var trademark = internal.withObjectProperty("trademark");
    defaultText(trademark, "status", "CHECK_REQUIRED");
    defaultText(trademark, "description", SUPPLIER_CHECK);
  }

  private static void fillAnalysisSections(ObjectNode report, StoredManualReport stored) {
    defaultText(report.withObjectProperty("summary"), "description", MISSING_DATA);
    var external = report.withObjectProperty("externalDiagnosis");
    if (!external.has("keyword")) external.put("keyword", stored.keyword());
    defaultText(external, "originalKeyword", stored.keyword() == null ? stored.productName() : stored.keyword());
    var seasonality = external.withObjectProperty("seasonality");
    defaultText(seasonality, "type", "UNKNOWN");
    defaultText(seasonality, "description", MISSING_DATA);

    var strategy = report.withObjectProperty("investmentStrategy");
    strategy.withObjectProperty("swot");
    defaultText(strategy, "conclusion", MISSING_DATA);
    strategy.withObjectProperty("priceCompetitiveness");
    strategy.withObjectProperty("coupangRegistration");
    defaultText(strategy.withObjectProperty("channelEfficiency"), "contentCompetitionStatus", "COMING_SOON");
  }

  private static void fillSimulation(ObjectNode simulation, StoredManualReport stored) {
    simulation.put("wholesalePrice", stored.wholesalePrice()).put("shippingFee", stored.shippingFee())
        .put("minimumSellingPrice", stored.minimumSellingPrice());
    defaultText(simulation, "platform", "COUPANG");
    if (!simulation.hasNonNull("sellingPriceStep")) simulation.put("sellingPriceStep", 100);
    if (!simulation.hasNonNull("conversionRate")) simulation.put("conversionRate", 2);
    var costs = simulation.withObjectProperty("sellerCosts");
    for (String field : List.of("advertisingCost", "packagingCost", "returnExchangeCost", "otherCost")) {
      if (!costs.hasNonNull(field)) costs.put(field, 0);
    }
  }

  private static void addUnavailableFeatures(ObjectNode report) {
    var features = new LinkedHashSet<>(MANUAL_UNAVAILABLE);
    JsonNode saved = report.path("unavailableFeatures");
    if (!saved.isMissingNode() && !saved.isNull()) {
      if (!saved.isArray()) throw new IllegalStateException("Invalid stored unavailable features");
      for (JsonNode feature : saved) {
        if (!feature.isTextual()) throw new IllegalStateException("Invalid stored unavailable feature");
        features.add(feature.textValue());
      }
    }
    var array = report.putArray("unavailableFeatures");
    features.forEach(array::add);
  }

  private static void defaultText(ObjectNode section, String field, String value) {
    if (!section.hasNonNull(field)) section.put(field, value);
  }
}
