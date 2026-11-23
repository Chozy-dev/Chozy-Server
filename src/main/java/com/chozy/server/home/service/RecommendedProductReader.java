package com.chozy.server.home.service;

import com.chozy.server.home.dto.RecommendedProducts.RecommendedProduct;
import com.chozy.server.home.dto.RecommendedProducts.Source;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface RecommendedProductReader {
  /**
   * Reads up to size items in rank order from the latest completed publication for source.
   * An empty Optional means no completed publication. A completed empty publication has an asOf timestamp.
   * An unavailable data source is reported with RECOMMENDATIONS_UNAVAILABLE.
   */
  Optional<Publication> latest(Source source, int size);

  record Publication(OffsetDateTime asOf, List<RecommendedProduct> items) {
    public Publication {
      Objects.requireNonNull(asOf);
      items = List.copyOf(items);
    }
  }
}
