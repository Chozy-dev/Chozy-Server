package com.chozy.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
  @Bean
  OpenAPI chozyOpenApi() {
    return new OpenAPI()
        .info(new Info().title("Chozy API").version("v1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 응답의 accessToken 값만 입력하세요.")));
  }

  @Bean
  OperationCustomizer responseContracts() {
    return new OpenApiResponses();
  }

  @Bean
  OpenApiCustomizer responseSchemas() {
    return api -> {
      api.getComponents().addSchemas(OpenApiResponses.ERROR_SCHEMA, OpenApiResponses.errorSchema());
      api.getComponents().addSchemas(OpenApiResponses.EMPTY_SUCCESS_SCHEMA, OpenApiResponses.emptySuccessSchema());
      var profilePatch = api.getComponents().getSchemas().get("ProfilePatch");
      if (profilePatch != null) {
        profilePatch.setAnyOf(List.of(
            new Schema<>().required(List.of("nickname")), new Schema<>().required(List.of("email"))));
      }
    };
  }
}
