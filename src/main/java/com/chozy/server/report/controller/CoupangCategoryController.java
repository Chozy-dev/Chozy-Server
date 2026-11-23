package com.chozy.server.report.controller;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.report.dto.CoupangCategories;
import com.chozy.server.report.service.CoupangCategoryCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "리포트")
public class CoupangCategoryController {
  private static final String LISTED = "쿠팡 카테고리 목록 조회가 완료되었습니다.";

  @GetMapping("/search/coupang-categories")
  @Operation(summary = "쿠팡 카테고리 목록 조회")
  @OpenApiContract(success = LISTED)
  public ApiResponse<CoupangCategories> list() {
    return ApiResponse.success(200, LISTED, CoupangCategoryCatalog.list());
  }
}
