package com.chozy.server.report.dto;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.AnalysisStatus;
import com.chozy.server.analysis.dto.AnalysisType;
import com.chozy.server.report.dto.ReportSummary.InvestmentGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RecentAnalyses(
    List<RecentAnalysisItem> analyses, int page, int size, long totalElements, long totalPages, boolean hasNext) {
  public static final int MAX_PAGE_SIZE = 100;

  public record RecentAnalysisItem(
      long analysisId,
      @Schema(types = {"integer", "null"}) Long reportId,
      AnalysisType analysisType,
      AnalysisSource source,
      @Schema(types = {"string", "null"}) String productId,
      String productName,
      @Schema(types = {"string", "null"}) String productImageUrl,
      @Schema(types = {"string", "null"}) String keyword,
      @Schema(types = {"number", "null"}) BigDecimal score,
      InvestmentGrade investmentGrade,
      AnalysisStatus analysisStatus,
      @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime analyzedAt) {}
}
