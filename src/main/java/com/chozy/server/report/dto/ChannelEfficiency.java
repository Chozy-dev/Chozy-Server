package com.chozy.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record ChannelEfficiency(
    @Schema(types = {"string", "null"}) String competitionLevel,
    @Schema(types = {"number", "null"}) BigDecimal mobileSearchRatio,
    List<BidEstimate> bidEstimates,
    ShoppingAudience shoppingAudience,
    String contentCompetitionStatus,
    @Schema(types = {"string", "null"}) String notice) {
  public ChannelEfficiency {
    bidEstimates = bidEstimates == null ? List.of() : List.copyOf(bidEstimates);
  }

  public record BidEstimate(
      BigDecimal position,
      String device,
      @Schema(types = {"number", "null"}) BigDecimal bid,
      @Schema(types = {"number", "null"}) BigDecimal clicks,
      @Schema(types = {"number", "null"}) BigDecimal impressions,
      @Schema(types = {"number", "null"}) BigDecimal cost) {
  }

  public record ShoppingAudience(
      String categoryCode,
      String categoryName,
      @Schema(types = {"number", "null"}) BigDecimal maleRatio,
      @Schema(types = {"number", "null"}) BigDecimal femaleRatio,
      List<AgeGroup> ageGroups,
      @Schema(types = {"string", "null"}) String mainAgeGroup,
      String description) {
    public ShoppingAudience {
      ageGroups = ageGroups == null ? List.of() : List.copyOf(ageGroups);
    }

    public record AgeGroup(
        String ageGroup,
        BigDecimal ratio) {
    }
  }
}
