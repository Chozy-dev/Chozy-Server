package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record CoupangRegistration(
    @Schema(types = {"string", "null"}) String categoryCode,
    List<String> categoryPath,
    @Schema(types = {"string", "null"}) String categorySource,
    @Schema(types = {"number", "null"}) BigDecimal feeRate,
    @Schema(types = {"string", "null"}) String feeRule,
    List<Certification> requiredCertifications,
    List<ProductInformationNotice> productInformationNotices,
    @Schema(types = {"string", "null"}) String notice) {
  public CoupangRegistration {
    categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
    requiredCertifications = requiredCertifications == null ? List.of() : List.copyOf(requiredCertifications);
    productInformationNotices = productInformationNotices == null ? List.of() : List.copyOf(productInformationNotices);
  }

  public record Certification(
      String name,
      String status,
      @Schema(types = {"string", "null"}) String certificateNumber,
      @Schema(types = {"string", "null"}) String notice) {
  }

  public record ProductInformationNotice(
      String name,
      boolean required,
      @Schema(types = {"string", "null"}) String value) {
  }
}
