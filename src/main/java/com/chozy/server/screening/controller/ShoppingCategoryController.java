package com.chozy.server.screening.controller;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.screening.dto.Screenings.Categories;
import com.chozy.server.screening.service.ShoppingCategories;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "키워드 스크리닝")
public class ShoppingCategoryController {
  private static final String LISTED = "쇼핑인사이트 분야 목록 조회가 완료되었습니다.";

  @GetMapping("/search/categories")
  @Operation(summary = "쇼핑인사이트 분야 목록 조회")
  @OpenApiContract(success = LISTED)
  public ApiResponse<Categories> list() {
    return ApiResponse.success(200, LISTED, ShoppingCategories.list());
  }
}
