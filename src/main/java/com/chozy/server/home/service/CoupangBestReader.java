package com.chozy.server.home.service;

import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestCategory;
import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestProduct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface CoupangBestReader {
  /**
   * Reads categories and their rank-ordered items from one completed batch, the latest by snapshot date.
   * An empty Optional means no completed batch. An unavailable source is reported with COUPANG_BEST_UNAVAILABLE.
   */
  Optional<Publication> latest();

  record Publication(OffsetDateTime publishedAt, List<PublishedCategory> categories) {
    public Publication {
      Objects.requireNonNull(publishedAt);
      categories = List.copyOf(categories);
    }
  }

  record PublishedCategory(CoupangBestCategory category, List<CoupangBestProduct> items) {
    public PublishedCategory {
      Objects.requireNonNull(category);
      items = List.copyOf(items);
    }
  }
}
