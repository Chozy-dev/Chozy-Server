package com.chozy.server.analysis.service;

import com.chozy.server.integration.external.MarketApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@Profile({"database", "dbdev"})
@ConditionalOnProperty(name = "app.analysis.market.enabled", havingValue = "true")
@EnableConfigurationProperties(ManualMarketConfiguration.Settings.class)
class ManualMarketConfiguration {
  @Bean
  ManualAnalysisEngine marketAnalysisEngine(Settings settings, ObjectMapper mapper, Clock clock, Environment environment) {
    var source = new DriverManagerDataSource(
        settings.budgetDbUrl(), settings.budgetDbUsername(), settings.budgetDbPassword());
    var jdbcOptions = new Properties();
    jdbcOptions.setProperty("connectTimeout", "4");
    jdbcOptions.setProperty("socketTimeout", "6");
    jdbcOptions.setProperty("options", "-c statement_timeout=4000");
    source.setConnectionProperties(jdbcOptions);
    var jdbc = new JdbcTemplate(source);
    jdbc.setQueryTimeout(5);

    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(8));
    var client = new MarketApiClient(RestClient.builder().requestFactory(factory).build(), mapper, jdbc, clock,
        settings.coupangAccount(), settings.naverAccount(), settings.coupangAccessKey(), settings.coupangSecretKey(),
        settings.adKey(), settings.adSecret(), settings.adCustomerId(), settings.hubId(), settings.hubKey(),
        environment.matchesProfiles("prod"));
    return new MarketManualAnalysisEngine(client, mapper, clock);
  }

  @ConfigurationProperties("app.analysis.market")
  @Validated
  record Settings(@NotBlank String budgetDbUrl, @NotBlank String budgetDbUsername, @NotBlank String budgetDbPassword,
      @NotBlank String coupangAccount, @NotBlank String naverAccount, @NotBlank String coupangAccessKey,
      @NotBlank String coupangSecretKey, @NotBlank String adKey, @NotBlank String adSecret,
      @NotBlank String adCustomerId, @NotBlank String hubId, @NotBlank String hubKey) {
    @Override
    public String toString() {
      return "ManualMarketSettings";
    }
  }
}
