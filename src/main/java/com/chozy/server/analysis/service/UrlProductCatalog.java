package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.UrlAnalyses.Option;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import java.util.List;
import java.util.Optional;

public interface UrlProductCatalog {
  /**
   * Looks up a product by its normalized supplier key. Return empty for a confirmed absence
   * and throw {@link BusinessException} with {@link ErrorCode#URL_ANALYSIS_UNAVAILABLE}
   * when availability or option data cannot be determined.
   */
  Optional<Product> find(AnalysisSource source, String productId);

  /** Option prices are final wholesale prices, and option IDs are unique within the product. */
  record Product(String name, List<Option> options) {
    public Product {
      options = List.copyOf(options);
    }
  }
}
