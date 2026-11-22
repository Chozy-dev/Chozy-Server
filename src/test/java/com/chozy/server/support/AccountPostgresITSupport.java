package com.chozy.server.support;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.auth.dto.AuthDtos.LoginResult;
import com.chozy.server.auth.dto.AuthDtos.Signup;
import com.chozy.server.auth.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = AccountPostgresITSupport.ClockConfiguration.class,
    properties = "app.auth.unverified-signup-enabled=true")
@ActiveProfiles("database")
@AutoConfigureMockMvc
public abstract class AccountPostgresITSupport {
  protected static final String PASSWORD = "ExamplePassword123!";
  protected static final String NEW_PASSWORD = "NewExamplePassword456!";
  protected static final byte[] KEY = new byte[32];
  protected static final String V2_LOGIN = "existingv2";
  protected static final long V6_REPORT_ID;
  private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("chozy_account_test").withUsername("test").withPassword(UUID.randomUUID().toString());

  static {
    DB.start();
    flyway().target("2").load().migrate();
    try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var insert = connection.prepareStatement("""
            INSERT INTO chozy.members (login_id, password_hash, nickname, email, terms_agreed_at, privacy_agreed_at)
            VALUES (?, ?, 'legacy seller', 'existingv2@example.com', now(), now())
            """)) {
      insert.setString(1, V2_LOGIN);
      insert.setString(2, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD));
      insert.executeUpdate();
    } catch (SQLException ex) {
      throw new ExceptionInInitializerError(ex);
    }
    flyway().target("6").load().migrate();
    V6_REPORT_ID = seedV6Report();
    flyway().load().migrate();
  }

  private static long seedV6Report() {
    try (var connection = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        var insert = connection.prepareStatement("""
            WITH job AS (
              INSERT INTO chozy.manual_analysis_jobs
                (member_id, product_name, wholesale_price, shipping_fee, has_price_policy, minimum_selling_price,
                  keyword, requested_at, status, completed_at)
              SELECT id, 'Legacy lantern', 7000, 2500, true, 11000, 'lantern',
                     '2026-01-14T03:00:00Z'::timestamptz, 'COMPLETED', '2026-01-14T03:01:00Z'::timestamptz
              FROM chozy.members WHERE login_id = ?
              RETURNING id
            )
            INSERT INTO chozy.manual_analysis_reports (analysis_id, snapshot)
            SELECT id, '{"product":{"productName":"Legacy lantern"}}'::jsonb FROM job
            RETURNING id
            """)) {
      insert.setString(1, V2_LOGIN);
      try (var result = insert.executeQuery()) {
        if (!result.next()) throw new IllegalStateException("Missing V6 fixture report");
        return result.getLong(1);
      }
    } catch (SQLException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private static FluentConfiguration flyway() {
    return Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations("classpath:db/migration/chozy").schemas("chozy").defaultSchema("chozy");
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.datasource.username", DB::getUsername);
    registry.add("spring.datasource.password", DB::getPassword);
    registry.add("JWT_SECRET_BASE64", () -> Base64.getEncoder().encodeToString(KEY));
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected AuthService auth;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected TransactionTemplate transactions;
  @Autowired protected TestClock clock;

  @BeforeEach
  void resetClock() {
    clock.fixed = null;
  }

  protected Account account() {
    String loginId = "u" + UUID.randomUUID().toString().replace("-", "");
    auth.signup(new Signup(loginId, PASSWORD, "seller", loginId + "@example.com", true, true));
    return new Account(loginId, auth.login(new Login(loginId, PASSWORD)));
  }

  protected JsonNode response(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }

  protected record Account(String loginId, LoginResult tokens) {
    public long id() { return tokens.userId(); }
    public String bearer() { return "Bearer " + tokens.accessToken(); }
    public String email() { return loginId + "@example.com"; }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfiguration {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  protected static final class TestClock extends Clock {
    private volatile Instant fixed;

    public void set(String instant) {
      fixed = Instant.parse(instant);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      InstantSource source = this::instant;
      return source.withZone(zone);
    }

    @Override
    public Instant instant() {
      Instant value = fixed;
      return value == null ? Instant.now() : value;
    }
  }
}
