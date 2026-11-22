package com.chozy.server.member.repository;

import static org.assertj.core.api.Assertions.*;

import com.chozy.server.member.entity.Member;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** No externally supplied URL is accepted: all writes target this disposable container. */
@SpringBootTest
@ActiveProfiles("database")
class MemberDatabaseIT {
  static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("chozy_test").withUsername("chozy_test").withPassword(UUID.randomUUID().toString());
  static final Flyway FLYWAY;
  static {
    DB.start();
    FLYWAY = Flyway.configure().dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations("classpath:db/migration/chozy").schemas("chozy").defaultSchema("chozy")
        .cleanDisabled(true).load();
    FLYWAY.migrate();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry props) {
    props.add("spring.datasource.url", DB::getJdbcUrl);
    props.add("spring.datasource.username", DB::getUsername);
    props.add("spring.datasource.password", DB::getPassword);
    props.add("spring.flyway.enabled", () -> false);
    props.add("JWT_SECRET_BASE64", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
  }

  @Autowired MemberRepository members;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManager entityManager;

  Connection connection() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }

  void insert(Connection c, String login, String email, String di) throws SQLException {
    try (PreparedStatement s = c.prepareStatement("""
        INSERT INTO chozy.members(login_id,password_hash,nickname,email,terms_agreed_at,
            privacy_agreed_at,verified_di_hash,di_verified_at)
        VALUES (?, 'test-only-encoded-password', 'fixture', ?, now(), now(), ?, ?)
        """)) {
      s.setString(1, login);
      s.setString(2, email);
      s.setString(3, di);
      s.setTimestamp(4, di == null ? null : Timestamp.from(Instant.now()));
      s.executeUpdate();
    }
  }

  @Test
  void migrationIsValidatedAndNotAppliedTwice() throws Exception {
    FLYWAY.validate();
    assertThat(FLYWAY.migrate().migrationsExecuted).isZero();
    try (Connection c = connection(); Statement s = c.createStatement()) {
      var rows = s.executeQuery("SELECT count(*) FROM chozy.flyway_schema_history WHERE version IS NOT NULL AND success");
      rows.next();
      assertThat(rows.getInt(1)).isEqualTo(5);
      var collectorHistory = s.executeQuery("SELECT to_regclass('public.schema_migrations') IS NULL");
      collectorHistory.next();
      assertThat(collectorHistory.getBoolean(1)).isTrue();
    }
  }

  @Test
  void jpaNormalizesStoresAndReadsMember() {
    transactions.executeWithoutResult(status -> {
      Member saved = members.saveAndFlush(new Member("  Jpa.User  ", "encoded-fixture", " fixture ",
          "  Jpa.User@Example.COM  ", Instant.now(), Instant.now()));
      entityManager.clear();
      assertThat(members.findById(saved.getId()).orElseThrow().getEmail()).isEqualTo("jpa.user@example.com");
      assertThat(saved.getLoginId()).isEqualTo("jpa.user");
      status.setRollbackOnly();
    });
  }

  @Test
  void loginUniquenessSurvivesConcurrentRequests() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      Callable<String> attempt = () -> {
        gate.await();
        try (Connection c = connection()) {
          insert(c, "concurrent", UUID.randomUUID() + "@example.com", null);
          return "committed";
        } catch (SQLException ex) { return ex.getSQLState(); }
      };
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      gate.countDown();
      assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("committed", "23505");
    } finally { pool.shutdownNow(); }
  }

  @Test
  void emailDuplicatesAndNonCanonicalValuesAreRejected() throws Exception {
    try (Connection c = connection()) {
      insert(c, "email1", "shared@example.com", null);
      assertThatThrownBy(() -> insert(c, "email2", "shared@example.com", null))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
      for (String email : java.util.List.of("Shared@example.com", " shared@example.com ", "x\ty@example.com")) {
        assertThatThrownBy(() -> insert(c, "email3", email, null))
            .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23514");
      }
      assertThatThrownBy(() -> insert(c, " Mixed ", "other@example.com", null))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23514");
    }
  }

  @Test
  void syntheticVerifiedDiIsUniqueAndUnverifiedMembersMayHaveNull() throws Exception {
    // Synthetic constraint fixture only, in disposable DB. No issued or authentic DI is fabricated.
    String synthetic = "synthetic_constraint_fixture_00000001";
    try (Connection c = connection()) {
      insert(c, "di1", "di1@example.com", synthetic);
      assertThatThrownBy(() -> insert(c, "di2", "di2@example.com", synthetic))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
      insert(c, "null1", "null1@example.com", null);
      insert(c, "null2", "null2@example.com", null);
      assertThatThrownBy(() -> c.createStatement().executeUpdate(
          "UPDATE chozy.members SET di_verified_at=now() WHERE login_id='null1'"))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23514");
    }
  }

  @Test
  void failedDuplicateRollsBackWholeTransactionAndConnectionRecovers() throws Exception {
    try (Connection c = connection()) {
      c.setAutoCommit(false);
      insert(c, "rollback", "rollback@example.com", null);
      assertThatThrownBy(() -> insert(c, "rollback", "duplicate@example.com", null))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
      assertThatThrownBy(() -> c.createStatement().execute("SELECT 1"))
          .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("25P02");
      c.rollback();
      try (var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM chozy.members WHERE login_id='rollback'")) {
        r.next();
        assertThat(r.getInt(1)).isZero();
      }
      insert(c, "rollback", "rollback@example.com", null);
      c.commit();
    }
  }
}
