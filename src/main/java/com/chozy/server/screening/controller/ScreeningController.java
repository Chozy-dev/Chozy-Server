package com.chozy.server.screening.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.screening.dto.Screenings.*;
import com.chozy.server.screening.service.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search/screenings")
@Profile({"database", "dbdev"})
@Tag(name = "키워드 스크리닝")
public class ScreeningController {
  private static final String SCREENED = "키워드 스크리닝이 완료되었습니다.";
  private static final String RECENT = "최근 키워드 스크리닝 기록 조회가 완료되었습니다.";
  private static final String SHOPPING = "키워드 쇼핑 반응 분석이 완료되었습니다.";

  private final ScreeningService screenings;

  public ScreeningController(ScreeningService screenings) {
    this.screenings = screenings;
  }

  @PostMapping
  @Operation(summary = "키워드 스크리닝")
  @OpenApiContract(success = SCREENED, optionalBearer = true, errors = {SCREENING_UNAVAILABLE})
  public ApiResponse<ScreeningResult> screen(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ScreeningRequest request) {
    return ApiResponse.success(200, SCREENED, screenings.screen(member, request));
  }

  @GetMapping("/recent")
  @Operation(summary = "최근 키워드 스크리닝 기록 조회")
  @SecurityRequirement(name = "bearerAuth")
  @OpenApiContract(success = RECENT)
  public ApiResponse<RecentScreenings> recent(@AuthenticationPrincipal AuthenticatedMember member) {
    return ApiResponse.success(200, RECENT, screenings.recent(member));
  }

  @PostMapping("/{screeningId}/shopping-analysis")
  @Operation(summary = "키워드 쇼핑 반응 분석")
  @OpenApiContract(success = SHOPPING, optionalBearer = true, errors = {NOT_FOUND, SCREENING_UNAVAILABLE})
  public ApiResponse<ShoppingResult> shopping(@AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String screeningId, @Valid @RequestBody ShoppingRequest request) {
    return ApiResponse.success(200, SHOPPING, screenings.shopping(member, screeningId, request));
  }
}
