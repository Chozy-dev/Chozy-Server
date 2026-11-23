package com.chozy.server.home.controller;

import static com.chozy.server.common.error.ErrorCode.INVALID_INPUT;
import static com.chozy.server.common.error.ErrorCode.MISSING_PARAMETER;
import static com.chozy.server.common.error.ErrorCode.RECOMMENDATIONS_UNAVAILABLE;
import static com.chozy.server.common.error.ErrorCode.TYPE_MISMATCH;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.home.dto.RecommendedProducts;
import com.chozy.server.home.dto.RecommendedProducts.Source;
import com.chozy.server.home.service.RecommendedProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "홈")
public class RecommendedProductController {
  private static final String LISTED = "공급 조건 우수 상품 조회가 완료되었습니다.";

  private final RecommendedProductService recommendations;

  public RecommendedProductController(RecommendedProductService recommendations) {
    this.recommendations = recommendations;
  }

  @GetMapping("/home/products/recommended")
  @Operation(summary = "공급 조건 우수 상품 목록 조회",
      description = "소싱처별 최신 완료 발행본을 순위순으로 조회합니다. 완료 발행본이 없으면 갱신 시각은 null입니다.")
  @OpenApiContract(success = LISTED,
      errors = {INVALID_INPUT, MISSING_PARAMETER, TYPE_MISMATCH, RECOMMENDATIONS_UNAVAILABLE})
  public ApiResponse<RecommendedProducts> listRecommendedProducts(@RequestParam Source source,
      @RequestParam(defaultValue = "10") @Min(1) @Max(1000) int size) {
    return ApiResponse.success(200, LISTED, recommendations.list(source, size));
  }
}
