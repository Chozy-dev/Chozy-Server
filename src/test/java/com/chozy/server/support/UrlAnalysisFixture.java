package com.chozy.server.support;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.UrlAnalyses.Option;
import com.chozy.server.analysis.service.ManualAnalysisEngine.Result;
import com.chozy.server.analysis.service.UrlAnalysisEngine;
import com.chozy.server.analysis.service.UrlProductCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class UrlAnalysisFixture {
  @Bean
  UrlProductCatalog urlProductCatalog() {
    return (source, id) -> {
      if (source == AnalysisSource.DOMEME && id.equals("12345678")) {
        return Optional.of(new UrlProductCatalog.Product("Lantern", List.of(
            new Option("RED", "Red", new BigDecimal("8000"), BigDecimal.TEN),
            new Option("BLUE", "Blue", new BigDecimal("8500"), null))));
      }
      if (source == AnalysisSource.OWNERCLAN && id.equals("Wabc123")) {
        return Optional.of(new UrlProductCatalog.Product(null, List.of(
            new Option("SMALL", "Small", new BigDecimal("8000"), null),
            new Option("LARGE", "Large", new BigDecimal("8000.00"), null))));
      }
      return Optional.empty();
    };
  }

  @Bean
  UrlAnalysisEngine urlAnalysisEngine(ObjectMapper mapper) {
    return (analysisId, input) -> {
      if ("FAIL".equals(input.keyword())) throw new IllegalStateException("Fixture failure");
      var snapshot = mapper.createObjectNode();
      snapshot.putObject("summary").put("score", 80);
      snapshot.putObject("externalDiagnosis").put("keyword", input.keyword());
      var product = snapshot.putObject("product").put("productName", "Fixture product");
      if ("RED".equals(input.selectedOptionId())) {
        product.putObject("selectedOption").put("optionName", "Red").put("price", 8000);
      }
      return new Result(snapshot, "EXEMPT".equals(input.keyword()));
    };
  }
}
