package com.chozy.server.auth.controller;

import com.chozy.server.auth.dto.AuthDtos.*;
import com.chozy.server.auth.service.AuthService;
import com.chozy.server.common.response.ApiResponse;
import com.chozy.server.common.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Profile({"database", "dbdev"})
public class AuthController {
  private final AuthService service;
  public AuthController(AuthService service) { this.service = service; }
  @PostMapping("/signup")
  @Operation(summary = "개발용 일반 회원가입 (본인인증 미연동)")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SignupResult> signup(@Valid @RequestBody Signup request) {
    return ApiResponse.success(201, "회원가입이 완료되었습니다.", service.signup(request));
  }
  @GetMapping("/check-login-id")
  @Operation(summary = "아이디 중복 확인")
  public ApiResponse<Availability> check(@RequestParam String loginId) {
    return ApiResponse.success(200, "아이디 중복 확인이 완료되었습니다.", service.checkLoginId(loginId));
  }
  @PostMapping("/login")
  @Operation(summary = "아이디·비밀번호 로그인")
  public ApiResponse<LoginResult> login(@Valid @RequestBody Login request) {
    return ApiResponse.success(200, "로그인이 완료되었습니다.", service.login(request));
  }
  @PostMapping("/reissue")
  @Operation(summary = "Refresh Token Rotation")
  public ApiResponse<Tokens> reissue(@Valid @RequestBody TokenRequest request) {
    return ApiResponse.success(200, "토큰 재발급이 완료되었습니다.", service.reissue(request.refreshToken()));
  }
  @PostMapping("/logout")
  @Operation(summary = "Refresh Token 무효화")
  @SecurityRequirement(name = "bearerAuth")
  public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody TokenRequest request) {
    service.logout(member.memberId(), request.refreshToken());
    return ApiResponse.success(200, "로그아웃이 완료되었습니다.", null);
  }
}
