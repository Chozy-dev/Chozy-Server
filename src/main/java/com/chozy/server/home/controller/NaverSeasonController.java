package com.chozy.server.home.controller;

import static com.chozy.server.common.error.ErrorCode.INVALID_INPUT;
import static com.chozy.server.common.error.ErrorCode.NAVER_SEASON_UNAVAILABLE;
import static com.chozy.server.common.error.ErrorCode.TYPE_MISMATCH;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.home.dto.NaverCoupangComparison;
import com.chozy.server.home.dto.NaverSeasonKeywords;
import com.chozy.server.home.service.NaverSeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "홈")
public class NaverSeasonController {
  private static final String LISTED = "네이버 시즌 키워드 조회가 완료되었습니다.";
  private static final String COMPARED = "네이버·쿠팡 비교 조회가 완료되었습니다.";
  private static final String THEME = "시즌 테마 코드. 생략하면 최신 완료 발행본의 가장 작은 코드 선택";
  private final NaverSeasonService seasons;

  public NaverSeasonController(NaverSeasonService seasons) {
    this.seasons = seasons;
  }

  @GetMapping("/home/keywords/naver")
  @Operation(summary = "네이버 시즌 키워드 조회", description = "최신 완료 발행본에서 테마별 상위 10개를 조회합니다.")
  @OpenApiContract(success = LISTED, errors = {INVALID_INPUT, TYPE_MISMATCH, NAVER_SEASON_UNAVAILABLE})
  public ApiResponse<NaverSeasonKeywords> listNaverSeasonKeywords(
      @Parameter(description = THEME) @RequestParam(required = false) @Min(1) Integer themeCode) {
    return ApiResponse.success(200, LISTED, seasons.list(themeCode));
  }

  @GetMapping("/home/keywords/compare")
  @Operation(summary = "네이버·쿠팡 비교 조회",
      description = "같은 기준일의 완료 발행본을 비교합니다. 쿠팡 발행본이 없으면 빈 결과와 안내를 반환합니다.")
  @OpenApiContract(success = COMPARED, errors = {INVALID_INPUT, TYPE_MISMATCH, NAVER_SEASON_UNAVAILABLE})
  public ApiResponse<NaverCoupangComparison> compareNaverCoupangKeywords(
      @Parameter(description = THEME) @RequestParam(required = false) @Min(1) Integer themeCode) {
    return ApiResponse.success(200, COMPARED, seasons.compare(themeCode));
  }
}
