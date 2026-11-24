package com.chozy.server.support;

import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.analysis.service.ManualAnalysisEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.IntConsumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ManualAnalysisFixture {
  @Bean
  ManualAnalysisEngine manualAnalysisEngine(ObjectMapper mapper) {
    return new ManualAnalysisEngine() {
      @Override
      public Result analyze(long id, ManualAnalysisInput input) {
        if ("FAIL".equals(input.keyword())) throw new IllegalStateException("fixture failure");
        var snapshot = mapper.createObjectNode();
        snapshot.putObject("product").put("productName", input.productName());
        snapshot.putObject("internalDiagnosis").putObject("profitability")
            .put("wholesalePrice", input.wholesalePrice()).put("shippingFee", input.shippingFee());
        return new Result(snapshot, "EXEMPT".equals(input.keyword()));
      }

      @Override
      public Result analyze(long id, ManualAnalysisInput input, IntConsumer progress) {
        progress.accept(4);
        progress.accept(5);
        return analyze(id, input);
      }
    };
  }
}
