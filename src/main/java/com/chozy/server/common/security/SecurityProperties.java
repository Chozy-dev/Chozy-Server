package com.chozy.server.common.security;

import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(boolean docsPublic, List<String> allowedOrigins) {
  public SecurityProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  @AssertTrue(message = "CORS origins must be explicit HTTP(S) origins without wildcards")
  public boolean isOriginsValid() {
    return allowedOrigins.stream()
        .allMatch(
            origin -> {
              try {
                var uri = java.net.URI.create(origin);
                return !origin.contains("*")
                    && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty());
              } catch (IllegalArgumentException ex) {
                return false;
              }
            });
  }
}
