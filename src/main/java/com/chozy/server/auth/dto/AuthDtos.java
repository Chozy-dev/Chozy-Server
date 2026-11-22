package com.chozy.server.auth.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;

public final class AuthDtos {
  private AuthDtos() {}
  public static String normalize(String value) {
    return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
  }
  public record Signup(
      @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_.-]{0,99}") String loginId,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Size(max = 100) String nickname,
      @NotBlank @Email @Size(max = 320) String email,
      @NotNull @AssertTrue Boolean termsAgreed, @NotNull @AssertTrue Boolean privacyAgreed) {
    public Signup {
      loginId = normalize(loginId);
      email = normalize(email);
      nickname = nickname == null ? null : nickname.strip();
    }
    @Override public String toString() { return "Signup[redacted]"; }
  }
  public record Login(@NotBlank @Size(max = 100) String loginId,
      @NotBlank @Size(max = 72) String password) {
    public Login { loginId = normalize(loginId); }
    @Override public String toString() { return "Login[redacted]"; }
  }
  public record TokenRequest(@NotBlank @Size(max = 4096) String refreshToken) {
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
