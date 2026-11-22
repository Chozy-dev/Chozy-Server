package com.chozy.server.common.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseSafetyTest {
  final DatabaseSafetyEnvironmentPostProcessor guard = new DatabaseSafetyEnvironmentPostProcessor();

  @Test
  void productionMigrationIsRejectedBeforeConnecting() {
    var env = new MockEnvironment().withProperty("spring.flyway.enabled", "true");
    env.setActiveProfiles("prod", "database");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
  }

  @Test
  void missingPasswordAndProductionDevMixAreRejected() {
    var env = new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:55432/chozy_spring_dev")
        .withProperty("spring.datasource.username", "chozy_spring_dev");
    env.setActiveProfiles("dbdev");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
    env.setProperty("spring.datasource.password", "test-only");
    env.setActiveProfiles("prod", "dbdev");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
  }

  @Test
  void unsafeDevTargetAndMixedProfilesAreRejected() {
    var env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/production");
    env.setActiveProfiles("dbdev");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
    env.setActiveProfiles("offline", "database");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
  }

  @Test
  void isolatedDevTargetIsAcceptedButAlternateFlywayTargetIsRejected() {
    var env = new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:55432/chozy_spring_dev")
        .withProperty("spring.datasource.username", "chozy_spring_dev")
        .withProperty("spring.datasource.password", "test-only")
        .withProperty("spring.flyway.enabled", "true");
    env.setActiveProfiles("dbdev");
    assertThatCode(() -> guard.postProcessEnvironment(env, null)).doesNotThrowAnyException();
    env.setProperty("spring.flyway.url", "jdbc:postgresql://localhost:5432/production");
    assertThatIllegalStateException().isThrownBy(() -> guard.postProcessEnvironment(env, null));
  }
}
