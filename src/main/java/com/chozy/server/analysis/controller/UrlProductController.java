package com.chozy.server.analysis.controller;

import static com.chozy.server.common.error.ErrorCode.URL_ANALYSIS_UNAVAILABLE;

import com.chozy.server.analysis.dto.UrlAnalyses.Resolution;
import com.chozy.server.analysis.dto.UrlAnalyses.ResolveRequest;
import com.chozy.server.analysis.service.UrlProductResolver;
import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "정밀분석")
public class UrlProductController {
  private static final String RESOLVED = "상품 URL 판별이 완료되었습니다.";

  private final UrlProductResolver resolver;

  public UrlProductController(UrlProductResolver resolver) {
    this.resolver = resolver;
  }

  @PostMapping("/search/products/resolve-url")
  @Operation(summary = "상품 URL 판별")
  @OpenApiContract(success = RESOLVED, errors = URL_ANALYSIS_UNAVAILABLE)
  public ApiResponse<Resolution> resolve(@Valid @RequestBody ResolveRequest request) {
    return ApiResponse.success(200, RESOLVED, resolver.resolve(request.url()));
  }
}
