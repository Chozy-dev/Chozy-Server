package com.chozy.server.home.controller;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.home.dto.HomeReportUsage;
import com.chozy.server.usage.service.ReportUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "홈")
public class ReportUsageController {
  private static final String USAGE_READ = "정밀분석 사용량 조회가 완료되었습니다.";

  private final ReportUsageService usage;

  public ReportUsageController(ReportUsageService usage) {
    this.usage = usage;
  }

  @GetMapping("/home/report-usage")
  @Operation(summary = "무료 정밀분석 사용량 조회")
  @OpenApiContract(success = USAGE_READ)
  public ApiResponse<HomeReportUsage> reportUsage(@AuthenticationPrincipal AuthenticatedMember member) {
    return ApiResponse.success(200, USAGE_READ, HomeReportUsage.from(usage.current(member.memberId())));
  }
}
