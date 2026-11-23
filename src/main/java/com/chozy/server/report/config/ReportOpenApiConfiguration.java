package com.chozy.server.report.config;

import static com.chozy.server.common.config.OpenApiSchemas.allowNull;

import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.stream.Stream;
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
      allowNull(schemas.get("RecalculatedSummary"), "investmentGrade");
      allowNull(schemas.get("RecentAnalysisItem"), "investmentGrade");
      var patch = schemas.get("ReportInputPatch");
      if (patch != null) {
        patch.setAnyOf(Stream.of("expectedSellingPrice", "coupangCategoryCode", "keyword", "selectedOptionId", "cpc")
            .map(field -> new Schema<>().required(List.of(field))).toList());
      }
    };
  }
}
