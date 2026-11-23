package com.chozy.server.home.service;

import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonKeyword;
import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonTheme;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NaverSeasonReader {
  /**
   * Reads one latest completed Naver batch, with keywords in published rank order. With comparison requested,
   * also reads the completed Coupang batch with products for the same snapshot date, if any, and the counts of
   * distinct matching products for each keyword from that batch. Reads must use one consistent snapshot.
   * With comparison disabled, no Coupang data is needed. An empty Optional means no Naver publication.
   * An unavailable data source is reported with NAVER_SEASON_UNAVAILABLE.
   */
  Optional<Publication> latest(boolean includeComparison);

  record Publication(LocalDate snapshotDate, OffsetDateTime publishedAt, List<PublishedTheme> themes,
      ComparedPublication comparison) {
    public Publication {
      themes = List.copyOf(themes);
    }
  }

  record PublishedTheme(SeasonTheme theme, List<PublishedKeyword> items) {
    public PublishedTheme {
      items = List.copyOf(items);
    }
  }

  record PublishedKeyword(SeasonKeyword keyword, Long matchedProductCount) {}

  record ComparedPublication(LocalDate snapshotDate, OffsetDateTime publishedAt) {}
}
