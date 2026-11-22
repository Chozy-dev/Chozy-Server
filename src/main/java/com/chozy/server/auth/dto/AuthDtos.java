package com.chozy.server.auth.dto;

import static com.chozy.server.auth.service.PasswordPolicy.*;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;

public final class AuthDtos {
  private AuthDtos() {}
  public static String normalize(String value) {
    return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
  }
  public record Signup(
      @Schema(example = "chozy123", description = "앞뒤 공백과 대소문자를 정규화한 후 검증합니다.")
      @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_.-]{0,99}") String loginId,
      @Schema(example = "ExamplePassword123!", format = "password", description = "UTF-8 기준 최대 72바이트")
      @NotBlank @Size(min = MIN_LENGTH, max = MAX_LENGTH) String password,
      @Schema(example = "초보셀러") @NotBlank @Size(max = 100) String nickname,
      @Schema(example = "chozy123@example.com", format = "email") @NotBlank @Email @Size(max = 320) String email,
      @Schema(example = "true", description = "서비스 이용약관 동의. true 필수")
      @NotNull @AssertTrue Boolean termsAgreed,
      @Schema(example = "true", description = "개인정보 수집·이용 동의. true 필수")
      @NotNull @AssertTrue Boolean privacyAgreed) {
    public Signup {
      loginId = normalize(loginId);
      email = normalize(email);
      nickname = nickname == null ? null : nickname.strip();
    }
    @Override public String toString() { return "Signup[redacted]"; }
  }
  public record Login(@Schema(example = "chozy123") @NotBlank @Size(max = 100) String loginId,
      @Schema(example = "ExamplePassword123!", format = "password")
      @NotBlank @Size(max = MAX_LENGTH) String password) {
    public Login { loginId = normalize(loginId); }
    @Override public String toString() { return "Login[redacted]"; }
  }
  public record TokenRequest(
      @Schema(description = "로그인 또는 직전 재발급 응답에서 받은 Refresh Token", example = "<refreshToken>")
      @NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "TokenRequest[redacted]"; }
  }
  public record SignupResult(long userId, String loginId, String nickname, String email, Instant createdAt) {}
  public record Availability(String loginId, boolean available) {}
  public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    @Override public String toString() { return "Tokens[redacted]"; }
  }
  public record LoginResult(long userId, String nickname, String accessToken, String refreshToken,
      String tokenType, long expiresIn) {
    @Override public String toString() { return "LoginResult[redacted]"; }
  }
}
