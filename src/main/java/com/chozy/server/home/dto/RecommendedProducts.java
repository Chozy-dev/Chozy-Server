package com.chozy.server.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RecommendedProducts(
    @Schema(types = {"string", "null"}, format = "date-time",
        description = "완료 발행본의 갱신 시각. 완료 발행본이 없으면 null")
    OffsetDateTime updatedAt,
    List<RecommendedProduct> items) {
  public enum Source {
    DOMEME,
    OWNERCLAN
  }

  public record RecommendedProduct(
      int rank,
      long productId,
      @Schema(format = "uri") String productUrl,
      String productName,
      Source source,
      BigDecimal supplyPrice,
      @Schema(types = {"number", "null"}) BigDecimal supplyStabilityScore,
      @Schema(types = {"number", "null"}) BigDecimal supplierReliabilityScore,
      @Schema(types = {"integer", "null"}) Integer stockoutCount90d,
      @Schema(types = {"number", "null"}) BigDecimal supplierStockoutRate90d,
      @Schema(types = {"integer", "null"}) Integer shippingDays,
      BigDecimal score) {}
}
