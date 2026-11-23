package com.chozy.server.analysis.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.analysis.dto.ManualAnalysisResponses.Accepted;
import com.chozy.server.analysis.dto.ManualAnalysisResponses.Progress;
import com.chozy.server.analysis.dto.UrlAnalyses.SubmitRequest;
import com.chozy.server.analysis.dto.UrlAnalyses.UrlAccepted;
import com.chozy.server.analysis.service.ManualAnalysisService;
import com.chozy.server.analysis.service.UrlProductResolver;
import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
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
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "정밀분석")
public class ManualAnalysisController {
  private static final String ACCEPTED = "직접 입력 정밀분석 요청이 접수되었습니다.";
  private static final String URL_ACCEPTED = "상품 정밀분석 요청이 접수되었습니다.";
  private static final String PROGRESS = "정밀분석 진행 상태 조회가 완료되었습니다.";

  private final ManualAnalysisService service;
  private final UrlProductResolver urls;

  public ManualAnalysisController(ManualAnalysisService service, UrlProductResolver urls) {
    this.service = service;
    this.urls = urls;
  }

  @PostMapping("/search/analyses/url")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "상품 URL 정밀분석 요청")
  @OpenApiContract(success = URL_ACCEPTED, errors = {REPORT_LIMIT_EXHAUSTED, REPORT_SLOTS_RESERVED,
      URL_ANALYSIS_UNAVAILABLE})
  public ApiResponse<UrlAccepted> submitUrl(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody SubmitRequest input) {
    return ApiResponse.success(202, URL_ACCEPTED, service.submitUrl(member, urls.resolveForAnalysis(input)));
  }

  @PostMapping("/search/analyses/manual")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "직접 입력 정밀분석 요청")
  @OpenApiContract(success = ACCEPTED, errors = {REPORT_LIMIT_EXHAUSTED, REPORT_SLOTS_RESERVED, ANALYSIS_UNAVAILABLE})
  public ApiResponse<Accepted> submit(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ManualAnalysisInput input) {
    return ApiResponse.success(202, ACCEPTED, service.submit(member, input));
  }

  @GetMapping("/search/analyses/{analysisId}/status")
  @Operation(summary = "정밀분석 진행 상태 조회")
  @OpenApiContract(success = PROGRESS, errors = {INVALID_INPUT, TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<Progress> progress(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long analysisId) {
    return ApiResponse.success(200, PROGRESS, service.progress(member, analysisId));
  }
}
