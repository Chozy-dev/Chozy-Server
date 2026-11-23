package com.chozy.server.home.service;

import static com.chozy.server.common.error.ErrorCode.COUPANG_BEST_UNAVAILABLE;
import static java.util.Comparator.comparingLong;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.CoupangBestProducts;
import com.chozy.server.home.service.CoupangBestReader.Publication;
import com.chozy.server.home.service.CoupangBestReader.PublishedCategory;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CoupangBestService {
  private final ObjectProvider<CoupangBestReader> readers;

  public CoupangBestService(ObjectProvider<CoupangBestReader> readers) {
    this.readers = readers;
  }

  public CoupangBestProducts list(Long categoryId) {
    var reader = readers.getIfAvailable();
    if (reader == null) {
      throw new BusinessException(COUPANG_BEST_UNAVAILABLE);
    }
    return reader.latest()
        .map(publication -> selectCategory(publication, categoryId))
        .orElseGet(() -> new CoupangBestProducts(null, List.of(), null, List.of()));
  }

  private CoupangBestProducts selectCategory(Publication publication, Long categoryId) {
    var publishedCategories = publication.categories().stream()
        .sorted(comparingLong(entry -> entry.category().categoryId()))
        .toList();
    var selected = publishedCategories.stream()
        .filter(entry -> categoryId == null || entry.category().categoryId() == categoryId)
        .findFirst();
    Long selectedId = selected.map(entry -> entry.category().categoryId()).orElse(categoryId);
    return new CoupangBestProducts(publication.publishedAt(),
        publishedCategories.stream().map(PublishedCategory::category).toList(),
        selectedId, selected.map(PublishedCategory::items).orElseGet(List::of));
  }
}
