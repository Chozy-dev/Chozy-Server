package com.chozy.server.favorite.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.response.Pagination;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.favorite.dto.Favorites.*;
import com.chozy.server.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/favorites")
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관심상품")
public class FavoriteController {
  private static final String REGISTERED = "관심상품이 등록되었습니다.";
  private static final String EXISTING = "이미 등록된 관심상품입니다. 요청한 상태를 적용했습니다.";
  private static final String LISTED = "관심상품 목록 조회가 완료되었습니다.";
  private static final String FOUND = "관심상품 상세 조회가 완료되었습니다.";
  private static final String STATUS_CHANGED = "관심상품 상태가 변경되었습니다.";
  private static final String SALE_RECORDED = "판매 결과가 기록되었습니다.";
  private static final String DELETED = "관심상품이 삭제되었습니다.";

  private final FavoriteService favorites;

  public FavoriteController(FavoriteService favorites) {
    this.favorites = favorites;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "관심상품 등록")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200", description = EXISTING, useReturnTypeSchema = true)
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201", description = REGISTERED, useReturnTypeSchema = true)
  @OpenApiContract(success = REGISTERED, errors = {NOT_FOUND, FAVORITE_LIMIT_REACHED})
  public ResponseEntity<ApiResponse<Registration>> register(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody Register request) {
    var result = favorites.register(member, request);
    if (result.created()) return ResponseEntity.status(201).body(ApiResponse.success(201, REGISTERED, result.data()));
    return ResponseEntity.ok(ApiResponse.success(200, EXISTING, result.data()));
  }

  @GetMapping
  @Operation(summary = "관심상품 목록 조회")
  @OpenApiContract(success = LISTED, errors = {INVALID_INPUT, TYPE_MISMATCH})
  public ApiResponse<Listing> list(@AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(defaultValue = "INTERESTED") Status status, @RequestParam(defaultValue = "LATEST") Sort sort,
      @PositiveOrZero @RequestParam(defaultValue = "0") int page,
      @Min(1) @Max(Pagination.MAX_SIZE) @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(200, LISTED, favorites.list(member, status, sort, page, size));
  }

  @GetMapping("/{favoriteId}")
  @Operation(summary = "관심상품 상세 조회")
  @OpenApiContract(success = FOUND, errors = {INVALID_INPUT, TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<Detail> detail(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long favoriteId, @RequestParam(defaultValue = "30") int periodDays) {
    return ApiResponse.success(200, FOUND, favorites.detail(member, favoriteId, periodDays));
  }

  @PatchMapping("/{favoriteId}/status")
  @Operation(summary = "관심상품 상태 변경")
  @OpenApiContract(success = STATUS_CHANGED, errors = {TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<StatusChange> changeStatus(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long favoriteId, @Valid @RequestBody ChangeStatus request) {
    return ApiResponse.success(200, STATUS_CHANGED, favorites.changeStatus(member, favoriteId, request.status()));
  }

  @PostMapping("/{favoriteId}/sales-results")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "관심상품 판매 결과 기록")
  @OpenApiContract(success = SALE_RECORDED, errors = {TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<SalesResult> salesResult(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long favoriteId, @Valid @RequestBody RecordSale request) {
    return ApiResponse.success(201, SALE_RECORDED, favorites.recordSale(member, favoriteId, request.result()));
  }

  @DeleteMapping("/{favoriteId}")
  @Operation(summary = "관심상품 삭제")
  @OpenApiContract(success = DELETED, errors = {INVALID_INPUT, TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long favoriteId) {
    favorites.delete(member, favoriteId);
    return ApiResponse.success(200, DELETED, null);
  }
}
