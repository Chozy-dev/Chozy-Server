package com.chozy.server.report.dto;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.AnalysisType;
import java.time.OffsetDateTime;
import java.util.List;

public record ReportDetail(
    long reportId,
    long analysisId,
    AnalysisType analysisType,
    AnalysisSource source,
    OffsetDateTime analyzedAt,
    ReportProduct product,
    ReportSummary summary,
    InternalDiagnosis internalDiagnosis,
    ExternalDiagnosis externalDiagnosis,
    InvestmentStrategy investmentStrategy,
    InvestmentSimulation investmentSimulation,
    List<String> unavailableFeatures,
    List<String> notices) {
  public ReportDetail {
    unavailableFeatures = unavailableFeatures == null ? List.of() : List.copyOf(unavailableFeatures);
    notices = notices == null ? List.of() : List.copyOf(notices);
  }
}
