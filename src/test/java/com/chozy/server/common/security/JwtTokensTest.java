package com.chozy.server.common.security;

import static org.assertj.core.api.Assertions.*;
import com.chozy.server.common.error.*;
import java.time.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokensTest {
  static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  final Instant now = Instant.parse("2026-11-22T00:00:00Z");
  JwtTokens tokens(Instant time) { return new JwtTokens(KEY, 60, 120, Clock.fixed(time, ZoneOffset.UTC)); }
  void error(Runnable operation, ErrorCode code) {
    assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
        ex -> assertThat(ex.errorCode()).isEqualTo(code));
  }
  @Test void verifiesSignedMemberAndUniqueTokens() {
    String token = tokens(now).issue(42, "access");
    assertThat(tokens(now).verify(token, "access").memberId()).isEqualTo(42);
    assertThat(tokens(now).issue(42, "access")).isNotEqualTo(token);
  }
  @Test void expirationUsesSeparateCodesAtExactBoundary() {
    error(() -> tokens(now.plusSeconds(60)).verify(tokens(now).issue(42, "access"), "access"), ErrorCode.ACCESS_TOKEN_EXPIRED);
    error(() -> tokens(now.plusSeconds(120)).verify(tokens(now).issue(42, "refresh"), "refresh"), ErrorCode.REFRESH_TOKEN_EXPIRED);
  }
  @Test void tokenTypesCannotBeInterchanged() {
    error(() -> tokens(now).verify(tokens(now).issue(42, "refresh"), "access"), ErrorCode.INVALID_AUTHENTICATION);
    error(() -> tokens(now).verify(tokens(now).issue(42, "access"), "refresh"), ErrorCode.INVALID_AUTHENTICATION);
  }
  @Test void malformedAndForgedSignaturesAreRejected() {
    error(() -> tokens(now).verify("fake", "access"), ErrorCode.INVALID_AUTHENTICATION);
    String other = Base64.getEncoder().encodeToString("different-signature-key-for-testing-only-12345".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String forged = new JwtTokens(other, 60, 120, Clock.fixed(now, ZoneOffset.UTC)).issue(42, "access");
    error(() -> tokens(now).verify(forged, "access"), ErrorCode.INVALID_AUTHENTICATION);
  }
  @Test void weakOrMissingSecretsAndBadLifetimesAreRejected() {
    assertThatThrownBy(() -> new JwtTokens("", 60, 120, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwtTokens(KEY, 0, 120, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
  }
}
