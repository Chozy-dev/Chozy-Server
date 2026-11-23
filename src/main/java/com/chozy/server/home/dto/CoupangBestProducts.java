package com.chozy.server.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record CoupangBestProducts(
    @Schema(types = {"string", "null"}, format = "date-time",
        description = "최신 완료 발행본의 발행 시각. 발행본이 없으면 null") OffsetDateTime updatedAt,
    List<CoupangBestCategory> categories,
    @Schema(types = {"integer", "null"}, description = "선택한 카테고리 ID. 선택할 수 없으면 null")
    Long selectedCategoryId,
    List<CoupangBestProduct> items) {
  public record CoupangBestCategory(long categoryId, String categoryName) {}

  public record CoupangBestProduct(
      int rank,
      long productId,
      String productName,
      BigDecimal price,
      boolean isRocketDelivery,
      @Schema(types = {"integer", "null"}) Integer previousRank,
      @Schema(types = {"integer", "null"}) Integer rankChange,
      @Schema(description = "전일 발행 기준이 없으면 null") RankStatus rankStatus) {}

  public enum RankStatus {
    UP,
    DOWN,
    SAME,
    NEW
  }
}
