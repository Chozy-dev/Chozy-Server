package com.chozy.server.report.config;

import static com.chozy.server.common.config.OpenApiSchemas.allowNull;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"database", "dbdev"})
class ReportOpenApiConfiguration {
  @Bean
  OpenApiCustomizer nullableReportSchemas() {
    return api -> {
      var schemas = api.getComponents().getSchemas();
      allowNull(schemas.get("ReportProduct"), "selectedOption", "supplyConditions");
      allowNull(schemas.get("InternalDiagnosis"), "supplyStability", "supplierReliability");
      allowNull(schemas.get("ExternalDiagnosis"), "searchTrend");
      allowNull(schemas.get("ChannelEfficiency"), "shoppingAudience");
      allowNull(schemas.get("ReportSummary"), "investmentGrade");
      allowNull(schemas.get("RecentAnalysisItem"), "investmentGrade");
    };
  }
}
