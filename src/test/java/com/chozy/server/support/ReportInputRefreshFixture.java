package com.chozy.server.support;

import com.chozy.server.report.service.ReportInputDataProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ReportInputRefreshFixture {
  @Bean
  ReportInputDataProvider reportInputDataProvider() {
    return (current, draft, changes) -> {
      if (changes.keyword() != null) {
        draft.withObjectProperty("externalDiagnosis").put("monthlySearchCount", 200)
            .put("trendScore", 70).put("competitionScore", 80).put("seasonalityScore", 90);
      }
      if (changes.option() != null) {
        draft.withObjectProperty("internalDiagnosis").withObjectProperty("supplyStability")
            .put("stockQuantity", changes.option().stockQuantity()).put("score", 60);
        draft.withObjectProperty("internalDiagnosis").withObjectProperty("profitability")
            .put("marketPrice", 19000).put("shippingFee", 1500);
      }
    };
  }
}
