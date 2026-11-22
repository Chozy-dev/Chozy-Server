package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record InvestmentStrategy(
    Swot swot,
    String conclusion,
    PriceCompetitiveness priceCompetitiveness,
    CoupangRegistration coupangRegistration,
    ChannelEfficiency channelEfficiency,
    List<RecommendedKeyword> recommendedKeywords,
    List<ConditionalKeyword> conditionalKeywords,
    List<KeywordComparison> keywordComparison,
    List<PlatformComparison> platformComparison) {
  public InvestmentStrategy {
    recommendedKeywords = recommendedKeywords == null ? List.of() : List.copyOf(recommendedKeywords);
    conditionalKeywords = conditionalKeywords == null ? List.of() : List.copyOf(conditionalKeywords);
    keywordComparison = keywordComparison == null ? List.of() : List.copyOf(keywordComparison);
    platformComparison = platformComparison == null ? List.of() : List.copyOf(platformComparison);
  }

  public record Swot(
      List<String> strengths,
      List<String> weaknesses,
      List<String> opportunities,
      List<String> threats) {
    public Swot {
      strengths = strengths == null ? List.of() : List.copyOf(strengths);
      weaknesses = weaknesses == null ? List.of() : List.copyOf(weaknesses);
      opportunities = opportunities == null ? List.of() : List.copyOf(opportunities);
      threats = threats == null ? List.of() : List.copyOf(threats);
    }
  }

  public record PriceCompetitiveness(
      @Schema(types = {"string", "null"}) String priceBasis,
      @Schema(types = {"number", "null"}) BigDecimal referencePrice,
      @Schema(types = {"number", "null"}) BigDecimal marketPrice,
      @Schema(types = {"number", "null"}) BigDecimal rocketMedianPrice,
      @Schema(types = {"number", "null"}) BigDecimal marketMinPrice,
      @Schema(types = {"number", "null"}) BigDecimal marketMaxPrice,
      @Schema(types = {"number", "null"}) BigDecimal sellingPrice,
      @Schema(types = {"number", "null"}) BigDecimal sellingPriceDifferenceRate,
      @Schema(types = {"number", "null"}) BigDecimal rocketPriceDifferenceRate,
      @Schema(types = {"number", "null"}) BigDecimal marketPriceSampleCount,
      boolean marketPriceReferenceOnly,
      @Schema(types = {"string", "null"}) String searchQuery,
      @Schema(types = {"number", "null"}) BigDecimal searchedProductCount,
      @Schema(types = {"number", "null"}) BigDecimal attributeMatchedCount,
      @Schema(types = {"number", "null"}) BigDecimal sameSpecificationCount,
      @Schema(types = {"number", "null"}) BigDecimal excludedBundleCount,
      @Schema(types = {"number", "null"}) BigDecimal shippingAdjustedCount,
      @Schema(types = {"number", "null"}) BigDecimal excludedOutlierCount,
      @Schema(types = {"number", "null"}) BigDecimal otherSiteMinPrice,
      @Schema(types = {"number", "null"}) BigDecimal otherSiteMaxPrice,
      @Schema(types = {"boolean", "null"}) Boolean isDomemeLowestPrice,
      @Schema(types = {"string", "null"}) String notice) {
  }

  public record RecommendedKeyword(
      String keyword,
      BigDecimal monthlySearchCount,
      String competitionLevel,
      boolean isPrimary) {
  }

  public record ConditionalKeyword(
      String keyword,
      BigDecimal monthlySearchCount,
      String competitionLevel,
      String notice) {
  }

  public record KeywordComparison(
      String keyword,
      BigDecimal monthlySearchCount,
      String competitionLevel,
      @Schema(types = {"number", "null"}) BigDecimal minPrice,
      @Schema(types = {"number", "null"}) BigDecimal maxPrice) {
  }

  public record PlatformComparison(
      String platform,
      @Schema(types = {"number", "null"}) BigDecimal feeRate,
      @Schema(types = {"string", "null"}) String priceBasis,
      @Schema(types = {"number", "null"}) BigDecimal profitPerUnit,
      @Schema(types = {"number", "null"}) BigDecimal marginRate,
      @Schema(types = {"number", "null"}) BigDecimal rocketProductCount,
      @Schema(types = {"number", "null"}) BigDecimal monthlySearchCount,
      @Schema(types = {"string", "null"}) String competitionLevel) {
  }
}
