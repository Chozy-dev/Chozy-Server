package com.chozy.server.auth.controller;

import static com.chozy.server.common.error.ErrorCode.*;

import com.chozy.server.auth.dto.AuthDtos.*;
import com.chozy.server.auth.service.AuthService;
import com.chozy.server.common.config.OpenApiContract;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Profile({"database", "dbdev"})
@Tag(name = "회원 인증")
public class AuthController {
  private static final String SIGNED_UP = "회원가입이 완료되었습니다.";
  private static final String CHECKED = "아이디 중복 확인이 완료되었습니다.";
  private static final String LOGGED_IN = "로그인이 완료되었습니다.";
  private static final String REISSUED = "토큰 재발급이 완료되었습니다.";
  private static final String LOGGED_OUT = "로그아웃이 완료되었습니다.";

  private final AuthService service;
  public AuthController(AuthService service) { this.service = service; }
  @PostMapping("/signup")
  @Operation(summary = "개발용 일반 회원가입 (본인인증 미연동)")
  @OpenApiContract(success = SIGNED_UP, errors = {DUPLICATE, FORBIDDEN})
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SignupResult> signup(@Valid @RequestBody Signup request) {
    return ApiResponse.success(201, SIGNED_UP, service.signup(request));
  }
  @GetMapping("/check-login-id")
  @Operation(summary = "아이디 중복 확인")
  @OpenApiContract(success = CHECKED, errors = {INVALID_INPUT, MISSING_PARAMETER})
  public ApiResponse<Availability> check(@RequestParam
      @Parameter(description = "중복 여부를 확인할 아이디", example = "chozy123") String loginId) {
    return ApiResponse.success(200, CHECKED, service.checkLoginId(loginId));
  }
  @PostMapping("/login")
  @Operation(summary = "아이디·비밀번호 로그인")
  @OpenApiContract(success = LOGGED_IN)
  public ApiResponse<LoginResult> login(@Valid @RequestBody Login request) {
    return ApiResponse.success(200, LOGGED_IN, service.login(request));
  }
  @PostMapping("/reissue")
  @Operation(summary = "Access·Refresh Token 재발급",
      description = "본문의 Refresh Token으로 재발급합니다. 성공 후에는 응답의 새 Refresh Token을 사용하세요.")
  @OpenApiContract(success = REISSUED, errors = REFRESH_TOKEN_EXPIRED)
  public ApiResponse<Tokens> reissue(@Valid @RequestBody TokenRequest request) {
    return ApiResponse.success(200, REISSUED, service.reissue(request.refreshToken()));
  }
  @PostMapping("/logout")
  @Operation(summary = "Refresh Token 무효화")
  @OpenApiContract(success = LOGGED_OUT, errors = {REFRESH_TOKEN_EXPIRED, FORBIDDEN})
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody TokenRequest request) {
    service.logout(member, request.refreshToken());
    return ApiResponse.success(200, LOGGED_OUT, null);
  }
}
