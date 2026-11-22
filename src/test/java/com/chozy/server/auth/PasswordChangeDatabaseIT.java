package com.chozy.server.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.auth.dto.AuthDtos.TokenRequest;
import com.chozy.server.auth.dto.AuthDtos.Tokens;
import com.chozy.server.auth.service.AuthService;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.support.AccountPostgresITSupport;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PasswordChangeDatabaseIT extends AccountPostgresITSupport {
  @Test
  void changeInvalidatesEveryExistingAccessAndRefreshTokenOnlyForThatMember() throws Exception {
    var member = account();
    var other = account();
    var secondSession = auth.login(new Login(member.loginId(), PASSWORD));
    var rotated = auth.reissue(member.tokens().refreshToken());
    var result = response(change(member, PASSWORD, NEW_PASSWORD).andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다. 다시 로그인해 주세요.")));
    assertThat(result.get("data").isNull()).isTrue();
    for (String token : List.of(member.tokens().accessToken(), secondSession.accessToken(), rotated.accessToken())) {
      useAccess(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    }
    for (String token : List.of(member.tokens().refreshToken(), secondSession.refreshToken(), rotated.refreshToken())) {
      reissue(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    }
    assertThat(jdbc.sql("""
        SELECT count(*) FROM chozy.refresh_tokens WHERE member_id = :id AND revoked_at IS NULL
        """).param("id", member.id()).query(Long.class).single()).isZero();
    assertThatThrownBy(() -> auth.login(new Login(member.loginId(), PASSWORD))).isInstanceOf(BusinessException.class);
    var fresh = auth.login(new Login(member.loginId(), NEW_PASSWORD));
    useAccess(fresh.accessToken()).andExpect(status().isOk());
    reissue(fresh.refreshToken()).andExpect(status().isOk());
    useAccess(other.tokens().accessToken()).andExpect(status().isOk());
    reissue(other.tokens().refreshToken()).andExpect(status().isOk());
  }

  @Test
  void rejectsWrongSameShortAndOverlongMultibytePasswordsWithoutRevokingTokens() throws Exception {
    var member = account();
    change(member, "wrong", NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    for (String rejected : List.of(PASSWORD, "1234567", "가".repeat(25))) {
      change(member, PASSWORD, rejected).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    }
    useAccess(member.tokens().accessToken()).andExpect(status().isOk());
    reissue(member.tokens().refreshToken()).andExpect(status().isOk());
    change(member, PASSWORD, "가".repeat(24)).andExpect(status().isOk());
    assertThat(auth.login(new Login(member.loginId(), "가".repeat(24))).userId()).isEqualTo(member.id());
  }

  @Test
  void oldJwtWithoutCredentialVersionWorksUntilPasswordChange() throws Exception {
    var member = account();
    String legacyAccess = legacyToken(member.id(), "access");
    String legacyRefresh = legacyToken(member.id(), "refresh");
    jdbc.sql("""
        INSERT INTO chozy.refresh_tokens (member_id, token_hash, expires_at) VALUES (:id, :hash, :expiry)
        """).param("id", member.id()).param("hash", AuthService.hash(legacyRefresh))
        .param("expiry", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60)).update();
    useAccess(legacyAccess).andExpect(status().isOk());
    var rotated = response(reissue(legacyRefresh).andExpect(status().isOk())).get("data");
    change(member, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
    useAccess(legacyAccess).andExpect(status().isUnauthorized());
    useAccess(rotated.get("accessToken").asText()).andExpect(status().isUnauthorized());
    reissue(rotated.get("refreshToken").asText()).andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRacingWithPasswordChangeCannotLeaveAUsableSession() throws Exception {
    var member = account();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var rotation = executor.submit(() -> {
        start.await();
        try {
          return auth.reissue(member.tokens().refreshToken());
        } catch (BusinessException ex) {
          assertThat(ex.errorCode().code()).isEqualTo("A004");
          return (Tokens) null;
        }
      });
      var change = executor.submit(() -> {
        start.await();
        change(member, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
        return null;
      });
      start.countDown();
      change.get(15, TimeUnit.SECONDS);
      var rotated = rotation.get(15, TimeUnit.SECONDS);
      if (rotated != null) {
        useAccess(rotated.accessToken()).andExpect(status().isUnauthorized());
        reissue(rotated.refreshToken()).andExpect(status().isUnauthorized());
      }
      useAccess(member.tokens().accessToken()).andExpect(status().isUnauthorized());
      reissue(member.tokens().refreshToken()).andExpect(status().isUnauthorized());
    }
  }

  private ResultActions useAccess(String token) throws Exception {
    return mvc.perform(get("/home/report-usage").header("Authorization", "Bearer " + token));
  }

  private ResultActions change(Account member, String current, String replacement) throws Exception {
    return mvc.perform(patch("/my/password").header("Authorization", member.bearer())
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("currentPassword", current, "newPassword", replacement))));
  }

  private ResultActions reissue(String token) throws Exception {
    return mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(new TokenRequest(token))));
  }

  private String legacyToken(long memberId, String type) throws Exception {
    Instant now = Instant.now();
    var claims = new JWTClaimsSet.Builder().issuer("chozy").subject(Long.toString(memberId))
        .jwtID(UUID.randomUUID().toString()).claim("type", type).issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(300))).build();
    var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
    token.sign(new MACSigner(KEY));
    return token.serialize();
  }
}
