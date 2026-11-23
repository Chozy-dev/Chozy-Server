package com.chozy.server.member.controller;

import static com.chozy.server.common.error.ErrorCode.DUPLICATE;

import com.chozy.server.auth.service.AccountWithdrawalService;
import com.chozy.server.auth.service.PasswordChangeService;
import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.dto.*;
import com.chozy.server.member.service.DashboardService;
import com.chozy.server.member.service.MarketingConsentService;
import com.chozy.server.member.service.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/my")
@Profile({"database", "dbdev"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "마이페이지")
public class MyController {
  private static final String DASHBOARD_READ = "마이페이지 정보 조회가 완료되었습니다.";
  private static final String PROFILE_UPDATED = "회원 정보가 수정되었습니다.";
  private static final String PASSWORD_CHANGED = "비밀번호가 변경되었습니다. 다시 로그인해 주세요.";
  private static final String CONSENT_CHANGED = "광고성 정보 수신 동의 설정이 변경되었습니다.";
  private static final String WITHDRAWN = "회원탈퇴가 완료되었습니다.";

  private final DashboardService dashboard;
  private final MemberProfileService profile;
  private final PasswordChangeService passwords;
  private final MarketingConsentService marketing;
  private final AccountWithdrawalService accounts;

  public MyController(DashboardService dashboard, MemberProfileService profile, PasswordChangeService passwords,
      MarketingConsentService marketing, AccountWithdrawalService accounts) {
    this.dashboard = dashboard;
    this.profile = profile;
    this.passwords = passwords;
    this.marketing = marketing;
    this.accounts = accounts;
  }

  @GetMapping("/dashboard")
  @Operation(summary = "마이페이지 정보 및 이용 내역 조회")
  @OpenApiContract(success = DASHBOARD_READ)
  public ApiResponse<Dashboard> dashboard(@AuthenticationPrincipal AuthenticatedMember member) {
    return ApiResponse.success(200, DASHBOARD_READ, dashboard.get(member));
  }

  @PatchMapping("/profile")
  @Operation(summary = "회원 정보 수정", description = "닉네임 또는 이메일 중 하나 이상을 보냅니다.")
  @OpenApiContract(success = PROFILE_UPDATED, errors = DUPLICATE)
  public ApiResponse<ProfileResult> profile(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ProfilePatch request) {
    return ApiResponse.success(200, PROFILE_UPDATED, profile.update(member, request));
  }

  @PatchMapping("/password")
  @Operation(summary = "비밀번호 변경", description = "변경 후 기존 Access Token과 Refresh Token이 모두 무효화됩니다.")
  @OpenApiContract(success = PASSWORD_CHANGED)
  public ApiResponse<Void> password(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PasswordChange request) {
    passwords.change(member, request.currentPassword(), request.newPassword());
    return ApiResponse.success(200, PASSWORD_CHANGED, null);
  }

  @PatchMapping("/marketing-consent")
  @Operation(summary = "광고성 정보 수신 동의 변경")
  @OpenApiContract(success = CONSENT_CHANGED)
  public ApiResponse<MarketingConsent> marketingConsent(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody MarketingConsent.Change request) {
    return ApiResponse.success(200, CONSENT_CHANGED, marketing.change(member, request.agreed()));
  }

  @DeleteMapping("/account")
  @Operation(summary = "회원탈퇴", description = "현재 비밀번호 확인 후 계정을 비활성화하고 기존 토큰을 무효화합니다.")
  @OpenApiContract(success = WITHDRAWN)
  public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody AccountWithdrawal request) {
    accounts.withdraw(member, request.password());
    return ApiResponse.success(200, WITHDRAWN, null);
  }
}
