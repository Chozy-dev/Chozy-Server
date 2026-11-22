package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record InvestmentSimulation(
    String platform,
    BigDecimal wholesalePrice,
    BigDecimal shippingFee,
    @Schema(types = {"number", "null"}) BigDecimal feeRate,
    @Schema(types = {"number", "null"}) BigDecimal sellingPrice,
    @Schema(types = {"number", "null"}) BigDecimal targetMarginRate,
    @Schema(types = {"number", "null"}) BigDecimal recommendedSellingPrice,
    @Schema(types = {"number", "null"}) BigDecimal recommendedPriceMarginRate,
    @Schema(types = {"number", "null"}) BigDecimal sellingPriceMin,
    @Schema(types = {"number", "null"}) BigDecimal sellingPriceMax,
    BigDecimal sellingPriceStep,
    @Schema(types = {"number", "null"}) BigDecimal minimumSellingPrice,
    @Schema(types = {"boolean", "null"}) Boolean pricePolicyViolated,
    @Schema(types = {"number", "null"}) BigDecimal feeAmount,
    @Schema(types = {"number", "null"}) BigDecimal profitPerUnit,
    @Schema(types = {"number", "null"}) BigDecimal marginRate,
    @Schema(types = {"number", "null"}) BigDecimal breakEvenSellingPrice,
    @Schema(types = {"number", "null"}) BigDecimal breakEvenAdCostPerOrder,
    @Schema(types = {"number", "null"}) BigDecimal breakEvenRoas,
    @Schema(types = {"number", "null"}) BigDecimal cpc,
    @Schema(types = {"number", "null"}) BigDecimal targetSalesQuantity,
    BigDecimal conversionRate,
    @Schema(types = {"number", "null"}) BigDecimal requiredClicks,
    @Schema(types = {"number", "null"}) BigDecimal estimatedAdCost,
    @Schema(types = {"number", "null"}) BigDecimal totalMargin,
    @Schema(types = {"number", "null"}) BigDecimal adCostToMarginRatio,
    SellerCosts sellerCosts,
    @Schema(types = {"number", "null"}) BigDecimal actualProfit,
    @Schema(types = {"number", "null"}) BigDecimal actualProfitRate,
    @Schema(types = {"boolean", "null"}) Boolean targetMarginAchieved,
    List<String> warnings) {
  public InvestmentSimulation {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public record SellerCosts(
      BigDecimal advertisingCost,
      BigDecimal packagingCost,
      BigDecimal returnExchangeCost,
      BigDecimal otherCost) {
  }
}
