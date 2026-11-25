package com.chozy.server.home.config;

import com.chozy.server.home.repository.CollectorRecommendedProductReader;
import com.chozy.server.home.service.RecommendedProductReader;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@ConditionalOnProperty(name = "app.home.recommendations.enabled", havingValue = "true")
@EnableConfigurationProperties(RecommendedProductConfiguration.Settings.class)
class RecommendedProductConfiguration {
  @Bean
  RecommendedProductReader collectorRecommendedProductReader(Settings settings, ObjectMapper mapper) {
    var source = new DriverManagerDataSource(settings.dbUrl(), settings.dbUsername(), settings.dbPassword());
    var options = new Properties();
    options.setProperty("connectTimeout", "4");
    options.setProperty("socketTimeout", "6");
    options.setProperty("options", "-c statement_timeout=4000");
    source.setConnectionProperties(options);
    var jdbc = new JdbcTemplate(source);
    jdbc.setQueryTimeout(5);
    return new CollectorRecommendedProductReader(jdbc, mapper);
  }

  @ConfigurationProperties("app.home.recommendations")
  @Validated
  record Settings(@NotBlank String dbUrl, @NotBlank String dbUsername, @NotBlank String dbPassword) {
    @Override
    public String toString() {
      return "RecommendedProductSettings";
    }
  }
}
