package com.chozy.server.home.config;

import com.chozy.server.home.repository.CollectorCoupangBestReader;
import com.chozy.server.home.repository.CollectorNaverSeasonReader;
import com.chozy.server.home.service.CoupangBestReader;
import com.chozy.server.home.service.NaverSeasonReader;
import jakarta.validation.constraints.NotBlank;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@Profile({"database", "dbdev"})
@ConditionalOnProperty(name = "app.home.market-publications.enabled", havingValue = "true")
@EnableConfigurationProperties(MarketPublicationConfiguration.Settings.class)
class MarketPublicationConfiguration {
  private final JdbcTemplate jdbc;

  MarketPublicationConfiguration(Settings settings) {
    var source = new DriverManagerDataSource(settings.dbUrl(), settings.dbUsername(), settings.dbPassword());
    var options = new Properties();
    options.setProperty("connectTimeout", "4");
    options.setProperty("socketTimeout", "6");
    options.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=4000");
    source.setConnectionProperties(options);
    jdbc = new JdbcTemplate(source);
    jdbc.setQueryTimeout(5);
  }

  @Bean
  CoupangBestReader collectorCoupangBestReader() {
    return new CollectorCoupangBestReader(jdbc);
  }

  @Bean
  NaverSeasonReader collectorNaverSeasonReader() {
    return new CollectorNaverSeasonReader(jdbc);
  }

  @ConfigurationProperties("app.home.market-publications")
  @Validated
  record Settings(@NotBlank String dbUrl, @NotBlank String dbUsername, @NotBlank String dbPassword) {
    @Override
    public String toString() {
      return "MarketPublicationSettings";
    }
  }
}
