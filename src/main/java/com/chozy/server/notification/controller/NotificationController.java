package com.chozy.server.notification.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.response.Pagination;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.notification.dto.Notifications.*;
import com.chozy.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/home/notifications")
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "알림")
public class NotificationController {
  private static final String LISTED = "알림 목록 조회가 완료되었습니다.";
  private static final String READ = "알림을 읽음 처리했습니다.";
  private static final String READ_ALL = "모든 알림을 읽음 처리했습니다.";

  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  @Operation(summary = "알림 목록 조회")
  @OpenApiContract(success = LISTED, errors = {INVALID_INPUT, TYPE_MISMATCH})
  public ApiResponse<Listing> list(@AuthenticationPrincipal AuthenticatedMember member,
      @RequestParam(required = false) Boolean isRead, @Positive @RequestParam(defaultValue = "1") int page,
      @Min(1) @Max(Pagination.MAX_SIZE) @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(200, LISTED, notifications.list(member, isRead, page, size));
  }

  @PatchMapping("/{notificationId}/read")
  @Operation(summary = "개별 알림 읽음 처리")
  @OpenApiContract(success = READ, errors = {INVALID_INPUT, TYPE_MISMATCH, NOT_FOUND})
  public ApiResponse<ReadResult> markRead(@AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable long notificationId) {
    return ApiResponse.success(200, READ, notifications.markRead(member, notificationId));
  }

  @PatchMapping("/read-all")
  @Operation(summary = "전체 알림 읽음 처리")
  @OpenApiContract(success = READ_ALL)
  public ApiResponse<ReadAllResult> markAllRead(@AuthenticationPrincipal AuthenticatedMember member) {
    return ApiResponse.success(200, READ_ALL, notifications.markAllRead(member));
  }
}
