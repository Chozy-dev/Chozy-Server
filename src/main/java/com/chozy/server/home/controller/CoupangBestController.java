package com.chozy.server.home.controller;

import static com.chozy.server.common.error.ErrorCode.COUPANG_BEST_UNAVAILABLE;
import static com.chozy.server.common.error.ErrorCode.INVALID_INPUT;
import static com.chozy.server.common.error.ErrorCode.TYPE_MISMATCH;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.home.dto.CoupangBestProducts;
import com.chozy.server.home.service.CoupangBestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "홈")
public class CoupangBestController {
  private static final String LISTED = "쿠팡 베스트 상품 조회가 완료되었습니다.";

  private final CoupangBestService bestProducts;

  public CoupangBestController(CoupangBestService bestProducts) {
    this.bestProducts = bestProducts;
  }

  @GetMapping("/home/keywords/coupang")
  @Operation(summary = "쿠팡 베스트 상품 조회",
      description = "최신 완료 발행본의 카테고리와 베스트 상품을 조회합니다. 선택한 카테고리가 발행본에 없으면 상품 목록은 비어 있습니다.")
  @OpenApiContract(success = LISTED, errors = {INVALID_INPUT, TYPE_MISMATCH, COUPANG_BEST_UNAVAILABLE})
  public ApiResponse<CoupangBestProducts> listCoupangBestProducts(
      @Parameter(description = "쿠팡 카테고리 ID. 생략하면 최신 완료 발행본의 가장 작은 카테고리 ID 선택")
      @RequestParam(required = false) @Min(1) Long categoryId) {
    return ApiResponse.success(200, LISTED, bestProducts.list(categoryId));
  }
}
