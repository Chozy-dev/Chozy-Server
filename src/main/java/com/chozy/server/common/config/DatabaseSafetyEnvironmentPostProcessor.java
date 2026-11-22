package com.chozy.server.common.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs after config loading and before any bean can open a connection. */
public class DatabaseSafetyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
    Set<String> profiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));
    boolean devDatabase = profiles.contains("dbdev");
    boolean database = devDatabase || profiles.contains("database") || profiles.contains("prod");
    if (database && (profiles.contains("offline") || profiles.contains("dev"))) {
      throw new IllegalStateException("Offline and database profiles cannot be combined");
    }
    if (devDatabase) {
      if (profiles.contains("prod") || profiles.contains("database")
          || !"jdbc:postgresql://127.0.0.1:55432/chozy_spring_dev".equals(
              env.getProperty("spring.datasource.url"))
          || !"chozy_spring_dev".equals(env.getProperty("spring.datasource.username"))) {
        throw new IllegalStateException("dbdev requires the isolated local development database");
      }
      if (env.getProperty("spring.datasource.password", "").isBlank()) {
        throw new IllegalStateException("Development database password is required");
      }
    }
    if (env.getProperty("spring.flyway.enabled", Boolean.class, false)) {
      if (!devDatabase || env.containsProperty("spring.flyway.url")
          || env.containsProperty("spring.flyway.user")
          || env.containsProperty("spring.flyway.password")) {
        throw new IllegalStateException("Application Flyway is allowed only on dbdev datasource");
      }
    }
  }
}
