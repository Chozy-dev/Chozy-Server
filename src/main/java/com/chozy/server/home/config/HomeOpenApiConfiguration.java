package com.chozy.server.home.config;

import static com.chozy.server.common.config.OpenApiSchemas.allowNull;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class HomeOpenApiConfiguration {
  @Bean
  OpenApiCustomizer coupangBestSchemas() {
    return api -> allowNull(api.getComponents().getSchemas().get("CoupangBestProduct"), "rankStatus");
  }
}
