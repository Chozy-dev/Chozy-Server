package com.chozy.server.support;

import com.chozy.server.analysis.service.ManualAnalysisEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ManualAnalysisFixture {
  @Bean
  ManualAnalysisEngine manualAnalysisEngine(ObjectMapper mapper) {
    return (id, input) -> {
      if ("FAIL".equals(input.keyword())) throw new IllegalStateException("fixture failure");
      var snapshot = mapper.createObjectNode();
      snapshot.putObject("product").put("productName", input.productName());
      snapshot.putObject("internalDiagnosis").putObject("profitability")
          .put("wholesalePrice", input.wholesalePrice()).put("shippingFee", input.shippingFee());
      return new ManualAnalysisEngine.Result(snapshot, "EXEMPT".equals(input.keyword()));
    };
  }
}
