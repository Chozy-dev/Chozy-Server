package com.chozy.server.report.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.report.dto.ReportDetail;
import com.chozy.server.report.dto.ReportFeedback;
import com.chozy.server.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search/reports")
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "정밀분석 리포트")
public class ReportController {
  private static final String DETAIL = "정밀분석 리포트 조회가 완료되었습니다.";
  private static final String FEEDBACK = "리포트 점수 의견이 저장되었습니다.";

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/{reportId}")
  @Operation(summary = "정밀분석 리포트 상세 조회")
  @OpenApiContract(success = DETAIL, errors = {INVALID_INPUT, TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<ReportDetail> detail(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long reportId) {
    return ApiResponse.success(200, DETAIL, reports.detail(member, reportId));
  }

  @PostMapping("/{reportId}/feedback")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "리포트 점수 의견 저장")
  @OpenApiContract(success = FEEDBACK,
      errors = {TYPE_MISMATCH, NOT_FOUND, DUPLICATE, REPORT_SCORE_UNAVAILABLE})
  public ApiResponse<ReportFeedback> feedback(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long reportId, @Valid @RequestBody ReportFeedback.Request request) {
    return ApiResponse.success(201, FEEDBACK, reports.saveFeedback(member, reportId, request.feedback()));
  }
}
