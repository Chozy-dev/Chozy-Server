package com.chozy.server.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class UrlAnalyses {
  private UrlAnalyses() {}

  public enum UrlSource { DOMEME, OWNERCLAN, DOMEGGOOK, OTHER }

  public record ResolveRequest(@NotBlank @Size(max = 2048) String url) {}

  public record SubmitRequest(
      @NotBlank @Size(max = 2048) String url,
      @Size(max = 500) String keyword,
      @Size(max = 200) String selectedOptionId) {}

  public record Option(String optionId, String optionName, BigDecimal price,
      @Schema(types = {"number", "null"}) BigDecimal stockQuantity) {}

  public record Resolution(
      UrlSource source,
      @Schema(types = {"string", "null"}) String productId,
      boolean supported,
      @Schema(types = {"string", "null"}, allowableValues = {"DOMEME", "OWNERCLAN"}) AnalysisSource analysisType,
      boolean converted,
      String originalUrl,
      @Schema(types = {"string", "null"}) String resolvedUrl,
      boolean manualInputRequired,
      @Schema(types = {"string", "null"}) String notice,
      boolean optionSelectionRequired,
      List<Option> options) {}

  public record UrlInput(AnalysisSource source, String productId, String originalUrl, String resolvedUrl,
      String productName, String keyword, String selectedOptionId) {}

  public record UrlAccepted(
      long analysisId,
      @Schema(types = {"integer", "null"}) Long reportId,
      @Schema(allowableValues = {"DOMEME", "OWNERCLAN"}) AnalysisSource source,
      String productId,
      @Schema(types = {"string", "null"}) String productName,
      @Schema(types = {"string", "null"}) String keyword,
      @Schema(types = {"string", "null"}) String selectedOptionId,
      AnalysisStatus analysisStatus,
      OffsetDateTime requestedAt) {}
}
