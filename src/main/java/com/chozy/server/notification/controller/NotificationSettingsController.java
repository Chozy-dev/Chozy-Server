package com.chozy.server.notification.controller;

import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.notification.dto.NotificationSettings;
import com.chozy.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/my/notification-settings")
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "알림")
public class NotificationSettingsController {
  private static final String FOUND = "알림 설정 조회가 완료되었습니다.";
  private static final String UPDATED = "알림 설정이 변경되었습니다.";

  private final NotificationService notifications;

  public NotificationSettingsController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  @Operation(summary = "알림 설정 조회")
  @OpenApiContract(success = FOUND)
  public ApiResponse<NotificationSettings> settings(@AuthenticationPrincipal AuthenticatedMember member) {
    return ApiResponse.success(200, FOUND, notifications.settings(member));
  }

  @PatchMapping
  @Operation(summary = "알림 설정 변경")
  @OpenApiContract(success = UPDATED)
  public ApiResponse<NotificationSettings> update(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody NotificationSettings.Patch patch) {
    return ApiResponse.success(200, UPDATED, notifications.updateSettings(member, patch));
  }
}
