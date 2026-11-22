package com.chozy.server.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public final class ManualAnalysisResponses {
  private ManualAnalysisResponses() {}

  public record Accepted(
      long analysisId,
      @Schema(types = {"integer", "null"}) Long reportId,
      String source,
      String productName,
      @Schema(types = {"string", "null"}) String keyword,
      AnalysisStatus analysisStatus,
      OffsetDateTime requestedAt,
      List<String> availableFeatures,
      List<String> unavailableFeatures) {}

  public record Progress(
      long analysisId,
      AnalysisStatus analysisStatus,
      int currentStep,
      int totalSteps,
      @Schema(types = {"integer", "null"}) Long reportId,
      @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime completedAt,
      @Schema(types = {"string", "null"}) String errorMessage) {}
}
