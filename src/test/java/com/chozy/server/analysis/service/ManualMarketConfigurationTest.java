package com.chozy.server.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ManualMarketConfigurationTest {
  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withInitializer(application -> application.getEnvironment().setActiveProfiles("dbdev"))
      .withUserConfiguration(ManualMarketConfiguration.class)
      .withBean(ObjectMapper.class, ObjectMapper::new)
      .withBean(Clock.class, Clock::systemUTC);

  @Test
  void staysDisabledByDefaultAndRejectsIncompleteOptIn() {
    context.run(application -> assertThat(application).doesNotHaveBean(ManualAnalysisEngine.class));
    context.withPropertyValues("app.analysis.market.enabled=true")
        .run(application -> assertThat(application).hasFailed());
  }

  @Test
  void registersEngineWithCompleteSettingsWithoutConnectingAtStartup() {
    context.withPropertyValues(
        "app.analysis.market.enabled=true",
        "app.analysis.market.budget-db-url=jdbc:postgresql://127.0.0.1:1/fixture",
        "app.analysis.market.budget-db-username=fixture",
        "app.analysis.market.budget-db-password=fixture",
        "app.analysis.market.coupang-account=fixture",
        "app.analysis.market.naver-account=fixture",
        "app.analysis.market.coupang-access-key=fixture",
        "app.analysis.market.coupang-secret-key=fixture",
        "app.analysis.market.ad-key=fixture",
        "app.analysis.market.ad-secret=fixture",
        "app.analysis.market.ad-customer-id=fixture",
        "app.analysis.market.hub-id=fixture",
        "app.analysis.market.hub-key=fixture")
        .run(application -> assertThat(application).hasSingleBean(ManualAnalysisEngine.class));
  }
}
