package com.chozy.server.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.chozy.server.auth.dto.AuthDtos.*;
import com.chozy.server.auth.service.AuthService;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.security.JwtTokens;
import com.chozy.server.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {"app.auth.unverified-signup-enabled=true", "springdoc.api-docs.enabled=true", "app.security.docs-public=true"})
@ActiveProfiles("database")
@AutoConfigureMockMvc
class AuthDatabaseIT {
  static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("chozy_auth_test").withUsername("test").withPassword(UUID.randomUUID().toString());
  static {
    // Explicit ephemeral host binding stays stable across Docker container restart.
    try (var port = new java.net.ServerSocket(0)) {
      DB.setPortBindings(List.of("127.0.0.1:" + port.getLocalPort() + ":5432"));
    } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    DB.start();
    Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations("classpath:db/migration/chozy").schemas("chozy").defaultSchema("chozy").load().migrate();
  }
  @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", DB::getJdbcUrl);
    p.add("spring.datasource.username", DB::getUsername);
    p.add("spring.datasource.password", DB::getPassword);
    p.add("JWT_SECRET_BASE64", () -> KEY);
  }
  @Autowired AuthService auth;
  @Autowired MemberRepository members;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtTokens jwt;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  String id() { return "u" + UUID.randomUUID().toString().replace("-", ""); }
  Signup signup(String id) { return new Signup(id, "ExamplePassword123!", "seller", id + "@example.com", true, true); }
  LoginResult account() {
    String id = id(); auth.signup(signup(id));
    return auth.login(new Login(id, "ExamplePassword123!"));
  }
  String body(Object value) throws Exception { return mapper.writeValueAsString(value); }
  @Test void signupContractNormalizationHashAndUnverifiedState() throws Exception {
    String id = id();
    var input = new Signup("  " + id.toUpperCase(Locale.ROOT) + "  ", "ExamplePassword123!", " seller ", "  " + id.toUpperCase(Locale.ROOT) + "@EXAMPLE.COM  ", true, true);
    mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(input)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value(201))
        .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
        .andExpect(jsonPath("$.data.loginId").value(id)).andExpect(jsonPath("$.data.nickname").value("seller"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    var member = members.findByLoginId(id).orElseThrow();
    assertThat(member.getPasswordHash()).isNotEqualTo(input.password());
    assertThat(encoder.matches(input.password(), member.getPasswordHash())).isTrue();
    try (var c = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var s = c.prepareStatement("select verified_di_hash, di_verified_at from chozy.members where id=?")) {
      s.setLong(1, member.getId()); var r = s.executeQuery(); r.next();
      assertThat(r.getString(1)).isNull(); assertThat(r.getTimestamp(2)).isNull();
    }
    mvc.perform(get("/auth/check-login-id").param("loginId", " " + id.toUpperCase(Locale.ROOT) + " "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false));
    mvc.perform(get("/auth/check-login-id").param("loginId", id())).andExpect(jsonPath("$.data.available").value(true));
  }
  @ParameterizedTest
  @ValueSource(strings = {"loginId", "password", "nickname", "email", "termsAgreed", "privacyAgreed"})
  void requiredFields(String field) throws Exception {
    var input = mapper.valueToTree(signup(id())); ((com.fasterxml.jackson.databind.node.ObjectNode) input).remove(field);
    mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
  }
  @Test void invalidFieldsAndAgreements() throws Exception {
    for (Signup input : List.of(new Signup("bad id", "password1", "x", "x@y.com", true, true),
        new Signup(id(), "password1", "x", "bad-email", true, true),
        new Signup(id(), "password1", "x", "x@y.com", false, true),
        new Signup(id(), "password1", "x", "x@y.com", true, false))) {
      mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(input)))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    }
    mvc.perform(get("/auth/check-login-id").param("loginId", "bad id")).andExpect(status().isBadRequest());
    mvc.perform(get("/auth/check-login-id")).andExpect(jsonPath("$.code").value("C008"));
  }
  @Test void duplicateIdAndEmailUseConflict() throws Exception {
    String id = id(); auth.signup(signup(id));
    for (Signup input : List.of(signup(id), new Signup(id(), "password1", "x", id + "@example.com", true, true)))
      mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body(input)))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("C002"));
  }
  @Test void loginContractAndSafeFailures() throws Exception {
    String id = id(); auth.signup(signup(id));
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(new Login(id, "ExamplePassword123!"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").isNumber())
        .andExpect(jsonPath("$.data.nickname").value("seller")).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(3600));
    String response = null;
    for (Login input : List.of(new Login(id, "wrong"), new Login(id(), "wrong"))) {
      var result = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(input)))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004")).andReturn();
      if (response == null) response = result.getResponse().getContentAsString();
      else assertThat(result.getResponse().getContentAsString()).isEqualTo(response);
    }
  }
  @Test void rotationLogoutOwnershipAndAccessRemainder() throws Exception {
    var a = account(); var b = account();
    mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + a.accessToken())
        .contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(b.refreshToken()))))
        .andExpect(status().isForbidden());
    var rotated = mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(a.refreshToken()))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresIn").value(3600)).andReturn();
    String refresh = mapper.readTree(rotated.getResponse().getContentAsString()).at("/data/refreshToken").asText();
    assertThat(refresh).isNotEqualTo(a.refreshToken());
    mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(a.refreshToken()))))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + a.accessToken())
        .contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(refresh))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    assertThatThrownBy(() -> auth.reissue(refresh)).isInstanceOf(BusinessException.class);
    assertThat(jwt.verify(a.accessToken(), "access").memberId()).isEqualTo(a.userId());
    assertThat(auth.reissue(b.refreshToken()).accessToken()).isNotBlank();
  }
  @Test void securityExpiryForgeryTypesAndExactPublicRoutes() throws Exception {
    var a = account();
    String expired = new JwtTokens(KEY, 1, 1, Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-30))).issue(a.userId(), "access");
    for (String token : List.of("fake", a.refreshToken()))
      mvc.perform(get("/favorites").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.code").value("A004"));
    mvc.perform(get("/favorites").header("Authorization", "Bearer " + expired)).andExpect(jsonPath("$.code").value("A003"));
    mvc.perform(get("/favorites")).andExpect(status().isUnauthorized());
    mvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
    mvc.perform(post("/auth/future")).andExpect(status().isUnauthorized());
    mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(a.refreshToken()))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/favorites").header("Authorization", "Bearer " + a.accessToken())).andExpect(status().isNotFound());
    String expiredRefresh = new JwtTokens(KEY, 1, 1, Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-30))).issue(a.userId(), "refresh");
    mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(expiredRefresh))))
        .andExpect(jsonPath("$.code").value("A005"));
    for (String token : List.of("fake", a.accessToken(), new JwtTokens(Base64.getEncoder().encodeToString("different-signature-key-for-testing-only-12345".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 60, 120, Clock.systemUTC()).issue(a.userId(), "refresh")))
      mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(body(new TokenRequest(token))))
          .andExpect(jsonPath("$.code").value("A004"));
  }
  List<String> race(Callable<String> action) throws Exception {
    var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
    try {
      Callable<String> run = () -> { gate.await(); try { return action.call(); } catch (RuntimeException ex) { return "rejected"; } };
      var a = pool.submit(run); var b = pool.submit(run); gate.countDown();
      return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
  }
  @Test void concurrentSignupAndRotationHaveOneWinner() throws Exception {
    String id = id();
    assertThat(race(() -> { auth.signup(signup(id)); return "ok"; })).containsExactlyInAnyOrder("ok", "rejected");
    var login = auth.login(new Login(id, "ExamplePassword123!"));
    assertThat(race(() -> { auth.reissue(login.refreshToken()); return "ok"; })).containsExactlyInAnyOrder("ok", "rejected");
  }
  @Test void persistedHashSurvivesDatabaseRestartAndNewVerifier() throws Exception {
    var login = account();
    try (var c = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var s = c.prepareStatement("select token_hash from chozy.refresh_tokens where member_id=?")) {
      s.setLong(1, login.userId()); var r = s.executeQuery(); r.next();
      assertThat(r.getString(1)).isEqualTo(AuthService.hash(login.refreshToken())).hasSize(64);
    }
    DB.getDockerClient().restartContainerCmd(DB.getContainerId()).exec();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (true) {
      try (var c = java.sql.DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) { break; }
      catch (java.sql.SQLException ex) { if (System.nanoTime() > deadline) throw ex; Thread.sleep(200); }
    }
    var freshVerifier = new JwtTokens(KEY, 3600, 1209600, Clock.systemUTC());
    assertThat(freshVerifier.verify(login.refreshToken(), "refresh").memberId()).isEqualTo(login.userId());
    assertThat(auth.reissue(login.refreshToken()).refreshToken()).isNotEqualTo(login.refreshToken());
  }
  @Test void swaggerContainsFiveContractsAndOnlyLogoutRequiresBearer() throws Exception {
    var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var paths = mapper.readTree(result.getResponse().getContentAsString()).path("paths");
    for (String path : List.of("signup", "login", "check-login-id", "reissue", "logout"))
      assertThat(paths.has("/auth/" + path)).isTrue();
    assertThat(paths.at("/" + "~1auth~1logout" + "/post/security/0/bearerAuth").isArray()).isTrue();
    assertThat(paths.at("/~1auth~1login/post/security").isMissingNode()).isTrue();
  }
}
