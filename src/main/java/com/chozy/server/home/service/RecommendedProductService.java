package com.chozy.server.home.service;

import static com.chozy.server.common.error.ErrorCode.RECOMMENDATIONS_UNAVAILABLE;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.RecommendedProducts;
import com.chozy.server.home.dto.RecommendedProducts.Source;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RecommendedProductService {
  private final ObjectProvider<RecommendedProductReader> readers;

  public RecommendedProductService(ObjectProvider<RecommendedProductReader> readers) {
    this.readers = readers;
  }

  public RecommendedProducts list(Source source, int size) {
    var reader = readers.getIfAvailable();
    if (reader == null) {
      throw new BusinessException(RECOMMENDATIONS_UNAVAILABLE);
    }
    return reader.latest(source, size)
        .map(publication -> new RecommendedProducts(publication.updatedAt(), publication.items()))
        .orElseGet(() -> new RecommendedProducts(null, List.of()));
  }
}
