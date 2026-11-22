package com.chozy.server.report.config;

import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Set;
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
      // OpenAPI 3.1 references and enum constraints also apply to null unless placed in a separate branch.
      allowNull(schemas.get("ReportProduct"), "selectedOption", "supplyConditions");
      allowNull(schemas.get("InternalDiagnosis"), "supplyStability", "supplierReliability");
      allowNull(schemas.get("ExternalDiagnosis"), "searchTrend");
      allowNull(schemas.get("ChannelEfficiency"), "shoppingAudience");
      allowNull(schemas.get("ReportSummary"), "investmentGrade");
      allowNull(schemas.get("RecentAnalysisItem"), "investmentGrade");
    };
  }

  private static void allowNull(Schema<?> model, String... properties) {
    if (model == null) return;
    var fields = model.getProperties();
    for (String property : properties) {
      Schema<?> current = fields.get(property);
      fields.put(property, new Schema<>().anyOf(List.of(current, new Schema<>().types(Set.of("null")))));
    }
  }
}
