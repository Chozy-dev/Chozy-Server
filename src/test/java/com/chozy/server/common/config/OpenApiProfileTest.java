package com.chozy.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

class OpenApiProfileTest {
  @ParameterizedTest
  @CsvSource({"dev,true", "dbdev,true", "offline,false", "database,false", "prod,false"})
  void documentsArePublicByDefaultOnlyInDeveloperProfiles(String profile, boolean enabled) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    ConfigDataEnvironmentPostProcessor.applyTo(environment);

    assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isEqualTo(enabled);
    assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isEqualTo(enabled);
    assertThat(environment.getProperty("app.security.docs-public", Boolean.class)).isEqualTo(enabled);
  }
}
