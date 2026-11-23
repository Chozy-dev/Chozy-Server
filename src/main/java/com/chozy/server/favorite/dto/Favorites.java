package com.chozy.server.favorite.dto;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.common.response.Pagination;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class Favorites {
  private Favorites() {}

  public enum Status {
    INTERESTED, SELLING;

    @JsonCreator
    public static Status fromJson(String value) {
      return valueOf(value);
    }
  }

  public enum Sort { SCORE, CHANGE, LATEST }

  public enum SaleResult {
    GOOD, AVERAGE, POOR, NOT_LISTED;

    @JsonCreator
    public static SaleResult fromJson(String value) {
      return valueOf(value);
    }
  }

  public enum MarketPriceRefreshStatus { UNAVAILABLE }

  public record Register(
      @NotNull @Positive @JsonDeserialize(using = ReportIdDeserializer.class) Long reportId, @NotNull Status status) {}
  public record ChangeStatus(@NotNull Status status) {}
  public record RecordSale(@NotNull SaleResult result) {}

  public record Counts(long interested, long selling, long total) {
    public Counts(long interested, long selling) {
      this(interested, selling, interested + selling);
    }
  }

  public record LimitedCounts(long interested, long selling, long total, int limit) {
    public LimitedCounts(Counts counts, int limit) {
      this(counts.interested(), counts.selling(), counts.total(), limit);
    }
  }

  public record Registration(long favoriteId, long reportId, AnalysisSource source,
      @Schema(type = "null") String productId, String productName, Status status,
      @Schema(type = "null") Object selectedOption, @Schema(types = {"number", "null"}) BigDecimal savedScore,
      BigDecimal savedWholesalePrice, OffsetDateTime createdAt, long favoriteCount, int favoriteLimit) {}

  public record Listing(LimitedCounts counts, List<Item> items, Pagination pagination) {}

  public record Item(long favoriteId, long reportId, AnalysisSource source, @Schema(type = "null") String productId,
      String productName, @Schema(type = "null") String productImageUrl, String supplierName, Status status,
      @Schema(type = "null") Object selectedOption, @Schema(type = "null") List<?> otherOptionStockouts,
      BigDecimal wholesalePrice, @Schema(type = "null") BigDecimal stockQuantity,
      @Schema(types = {"number", "null"}) BigDecimal score, @Schema(types = {"number", "null"}) BigDecimal scoreChange,
      @Schema(types = {"number", "null"}) BigDecimal profitPerUnit,
      @Schema(types = {"number", "null"}) BigDecimal marginRate, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

  public record Detail(long favoriteId, long reportId, Status status, Product product,
      @Schema(type = "null") Object priceHistory, @Schema(type = "null") Object stockoutHistory, ScoreHistory scoreHistory,
      @Schema(type = "null") Object supplier, LatestSale salesResult, PurchaseLink purchaseLink,
      OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

  public record Product(AnalysisSource source, @Schema(type = "null") String productId, String productName,
      @Schema(type = "null") String productImageUrl, @Schema(type = "null") Object selectedOption,
      @Schema(type = "null") List<?> otherOptionStockouts, BigDecimal wholesalePrice,
      @Schema(types = {"number", "null"}) BigDecimal expectedSellingPrice, BigDecimal shippingFee,
      @Schema(types = {"number", "null"}) BigDecimal profitPerUnit,
      @Schema(types = {"number", "null"}) BigDecimal marginRate, @Schema(type = "null") BigDecimal stockQuantity,
      @Schema(type = "null") BigDecimal supplierReliabilityScore) {}

  public record ScoreHistory(@Schema(types = {"number", "null"}) BigDecimal savedScore,
      @Schema(types = {"number", "null"}) BigDecimal currentScore,
      @Schema(types = {"number", "null"}) BigDecimal scoreChange,
      @Schema(types = {"number", "null"}) BigDecimal savedSupplyConditionScore,
      @Schema(types = {"number", "null"}) BigDecimal currentSupplyConditionScore,
      @Schema(types = {"number", "null"}) BigDecimal savedMarketConditionScore,
      @Schema(types = {"number", "null"}) BigDecimal currentMarketConditionScore, OffsetDateTime savedAt,
      @Schema(type = "null") OffsetDateTime supplyDataUpdatedAt, @Schema(type = "null") OffsetDateTime marketPriceUpdatedAt,
      @Schema(type = "null") OffsetDateTime marketConditionUpdatedAt, MarketPriceRefreshStatus marketPriceRefreshStatus) {}

  public record LatestSale(SaleResult result, OffsetDateTime recordedAt) {}
  public record PurchaseLink(@Schema(type = "null") String url, boolean isAffiliateLink) {}
  public record StatusChange(long favoriteId, Status previousStatus, Status status, Counts counts,
      OffsetDateTime updatedAt) {}

  public record SalesResult(long salesResultId, long favoriteId, long reportId, AnalysisSource source,
      @Schema(type = "null") String productId, SaleResult result,
      @Schema(types = {"number", "null"}) BigDecimal scoreAtRecording,
      @Schema(types = {"number", "null"}) BigDecimal supplyConditionScoreAtRecording,
      @Schema(types = {"number", "null"}) BigDecimal marketConditionScoreAtRecording, OffsetDateTime recordedAt) {}

  static final class ReportIdDeserializer extends StdDeserializer<Long> {
    ReportIdDeserializer() {
      super(Long.class);
    }

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return parser.getLongValue();
      return (Long) context.handleUnexpectedToken(Long.class, parser);
    }
  }
}
