package com.chozy.server.screening.config;

import static com.chozy.server.common.config.OpenApiSchemas.allowNull;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"database", "dbdev"})
class ScreeningOpenApiConfiguration {
  @Bean
  OpenApiCustomizer nullableScreeningSchemas() {
    return api -> {
      var schemas = api.getComponents().getSchemas();
      allowNull(schemas.get("Keyword"), "quadrant", "searchTrend");
      allowNull(schemas.get("ShoppingResult"), "quadrant", "shoppingTrend");
    };
  }
}
