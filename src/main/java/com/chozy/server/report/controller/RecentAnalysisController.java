package com.chozy.server.report.controller;

import static com.chozy.server.common.error.ErrorCode.INVALID_INPUT;
import static com.chozy.server.common.error.ErrorCode.TYPE_MISMATCH;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.report.dto.RecentAnalyses;
import com.chozy.server.report.service.RecentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "정밀분석")
public class RecentAnalysisController {
  private static final String RECENT = "최근 정밀분석 기록 조회가 완료되었습니다.";

  private final RecentAnalysisService history;

  public RecentAnalysisController(RecentAnalysisService history) {
    this.history = history;
  }

  @GetMapping("/search/analyses/recent")
  @Operation(summary = "최근 정밀분석 기록 조회")
  @OpenApiContract(success = RECENT, errors = {INVALID_INPUT, TYPE_MISMATCH})
  public ApiResponse<RecentAnalyses> recent(@AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(defaultValue = "0") @PositiveOrZero int page,
      @RequestParam(defaultValue = "10") @Positive @Max(RecentAnalyses.MAX_PAGE_SIZE) int size) {
    return ApiResponse.success(200, RECENT, history.recent(member, page, size));
  }
}
