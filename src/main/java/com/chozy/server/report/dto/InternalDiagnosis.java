package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record InternalDiagnosis(
    Profitability profitability,
    SupplyStability supplyStability,
    SupplierReliability supplierReliability,
    Trademark trademark) {

  public record Profitability(
      @Schema(types = {"number", "null"}) BigDecimal score,
      BigDecimal wholesalePrice,
      BigDecimal shippingFee,
      String shippingPaymentType,
      @Schema(types = {"number", "null"}) BigDecimal wholesalePriceRatio,
      @Schema(types = {"number", "null"}) BigDecimal marketPrice,
      @Schema(types = {"number", "null"}) BigDecimal expectedSellingPrice,
      @Schema(types = {"number", "null"}) BigDecimal referenceSellingPrice,
      @Schema(types = {"string", "null"}) String priceBasis,
      @Schema(types = {"boolean", "null"}) Boolean hasPricePolicy,
      @Schema(types = {"number", "null"}) BigDecimal minimumSellingPrice,
      @Schema(types = {"string", "null"}) String minimumSellingPriceLabel,
      @Schema(types = {"number", "null"}) BigDecimal feeRate,
      @Schema(types = {"number", "null"}) BigDecimal feeAmount,
      @Schema(types = {"number", "null"}) BigDecimal profitPerUnit,
      @Schema(types = {"number", "null"}) BigDecimal marginRate) {
  }

  public record SupplyStability(
      @Schema(types = {"number", "null"}) BigDecimal score,
      @Schema(types = {"number", "null"}) BigDecimal stockQuantity,
      boolean isUnlimitedStock,
      @Schema(types = {"number", "null"}) BigDecimal shippingDays,
      String shippingDaysType,
      @Schema(types = {"boolean", "null"}) Boolean fastShipping,
      @Schema(types = {"number", "null"}) BigDecimal stockoutCount90d,
      @Schema(types = {"number", "null"}) BigDecimal priceChangeCount90d,
      BigDecimal historyDays,
      @Schema(types = {"number", "null"}) BigDecimal stockScore,
      @Schema(types = {"number", "null"}) BigDecimal shippingScore,
      @Schema(types = {"number", "null"}) BigDecimal stockoutScore,
      @Schema(types = {"number", "null"}) BigDecimal priceStabilityScore,
      @Schema(types = {"string", "null"}) String notice) {
  }

  public record SupplierReliability(
      @Schema(types = {"number", "null"}) BigDecimal score,
      String evaluationBasis,
      @Schema(types = {"number", "null"}) BigDecimal reviewSatisfactionRate,
      @Schema(types = {"number", "null"}) BigDecimal reviewCount,
      @Schema(types = {"boolean", "null"}) Boolean isExcellentSeller,
      @Schema(types = {"number", "null"}) BigDecimal sellerGrade,
      @Schema(types = {"string", "null"}) String businessType,
      @Schema(types = {"string", "null"}) String taxInvoiceNotice,
      @Schema(types = {"string", "null"}) String vacationStartDate,
      @Schema(types = {"string", "null"}) String vacationEndDate,
      @Schema(types = {"number", "null"}) BigDecimal supplierStockoutRate90d,
      @Schema(types = {"number", "null"}) BigDecimal supplierSampleCount,
      String description) {
  }

  public record Trademark(
      String status,
      List<String> checkedKeywords,
      List<TrademarkMatch> matches,
      String description) {
    public Trademark {
      checkedKeywords = checkedKeywords == null ? List.of() : List.copyOf(checkedKeywords);
      matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public record TrademarkMatch(
        String keyword,
        String title,
        @Schema(types = {"string", "null"}) String applicationNumber,
        @Schema(types = {"string", "null"}) String applicationStatus,
        @Schema(types = {"string", "null"}) String registrationNumber,
        @Schema(types = {"string", "null"}) String registrationDate,
        @Schema(types = {"string", "null"}) String classificationCode,
        @Schema(types = {"string", "null"}) String applicantName,
        @Schema(types = {"string", "null"}) String regPrivilegeName) {
    }
  }
}
