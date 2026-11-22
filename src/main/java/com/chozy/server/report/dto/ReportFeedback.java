package com.chozy.server.report.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReportFeedback(
    long feedbackId,
    long reportId,
    @Schema(types = {"string", "null"}) String productId,
    BigDecimal score,
    @Schema(types = {"number", "null"}) BigDecimal supplyConditionScore,
    @Schema(types = {"number", "null"}) BigDecimal marketConditionScore,
    Opinion feedback,
    OffsetDateTime createdAt) {

  public record Request(@NotNull Opinion feedback) {}

  public enum Opinion {
    APPROPRIATE,
    TOO_HIGH,
    TOO_LOW;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Opinion fromJson(JsonNode value) {
      if (value == null || value.isNull()) return null;
      if (!value.isTextual()) throw new IllegalArgumentException("Expected a feedback string");
      return valueOf(value.textValue());
    }
  }
}
