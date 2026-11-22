package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record ReportProduct(
    @Schema(types = {"string", "null"}) String productId,
    String productName,
    @Schema(types = {"string", "null"}) String productImageUrl,
    @Schema(types = {"string", "null"}) String originalUrl,
    @Schema(types = {"string", "null"}) String resolvedUrl,
    @Schema(types = {"string", "null"}) String purchaseUrl,
    boolean isAffiliateLink,
    SelectedOption selectedOption,
    @Schema(types = {"string", "null"}) String manufacturer,
    @Schema(types = {"string", "null"}) String supplierName,
    @Schema(types = {"string", "null"}) String originCountry,
    @Schema(types = {"string", "null"}) String modelName,
    @Schema(types = {"string", "null"}) String sourceCategory,
    @Schema(types = {"string", "null"}) String popularProductClassification,
    SupplyConditions supplyConditions,
    boolean saleBlocked,
    List<String> saleWarnings,
    List<String> notices) {
  public ReportProduct {
    saleWarnings = saleWarnings == null ? List.of() : List.copyOf(saleWarnings);
    notices = notices == null ? List.of() : List.copyOf(notices);
  }

  public record SelectedOption(
      String optionId,
      String optionName,
      BigDecimal price,
      @Schema(types = {"number", "null"}) BigDecimal stockQuantity) {
  }

  public record SupplyConditions(
      @Schema(types = {"number", "null"}) BigDecimal returnShippingFee,
      @Schema(types = {"boolean", "null"}) Boolean doubleReturnFeeForFreeShipping,
      @Schema(types = {"number", "null"}) BigDecimal remoteAreaAdditionalShippingFee,
      @Schema(types = {"boolean", "null"}) Boolean imageReuseAllowed,
      @Schema(types = {"boolean", "null"}) Boolean exclusiveSale,
      @Schema(types = {"string", "null"}) String sellerNotice,
      @Schema(types = {"boolean", "null"}) Boolean returnAllowed,
      @Schema(types = {"string", "null"}) String returnRestrictionReason,
      @Schema(types = {"number", "null"}) BigDecimal maxBundleShippingQuantity,
      @Schema(types = {"string", "null"}) String shippingCutoffTime,
      @Schema(types = {"boolean", "null"}) Boolean taxExempt,
      @Schema(types = {"string", "null"}) String supplierNotice) {
  }
}
