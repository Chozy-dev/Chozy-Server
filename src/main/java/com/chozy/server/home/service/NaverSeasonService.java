package com.chozy.server.home.service;

import static com.chozy.server.common.error.ErrorCode.NAVER_SEASON_UNAVAILABLE;
import static java.util.Comparator.comparingInt;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.NaverCoupangComparison;
import com.chozy.server.home.dto.NaverCoupangComparison.ComparedKeyword;
import com.chozy.server.home.dto.NaverSeasonKeywords;
import com.chozy.server.home.service.NaverSeasonReader.Publication;
import com.chozy.server.home.service.NaverSeasonReader.PublishedKeyword;
import com.chozy.server.home.service.NaverSeasonReader.PublishedTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class NaverSeasonService {
  private static final int MAX_KEYWORDS = 10;
  private static final String SCOPE = "쿠팡은 서버가 수집한 베스트 카테고리만 비교한 결과입니다.";
  private static final String NO_NAVER = "완료된 네이버 시즌 키워드 발행본이 없습니다.";
  private static final String NO_COUPANG = "같은 기준일의 쿠팡 베스트 발행본이 없어 비교할 수 없습니다.";
  private final ObjectProvider<NaverSeasonReader> readers;

  public NaverSeasonService(ObjectProvider<NaverSeasonReader> readers) {
    this.readers = readers;
  }

  public NaverSeasonKeywords list(Integer themeCode) {
    return latest(false)
        .map(publication -> {
          var themes = themes(publication);
          var selected = select(themes, themeCode);
          return new NaverSeasonKeywords(publication.publishedAt(), themes.stream().map(PublishedTheme::theme).toList(),
              selected.map(entry -> entry.theme().themeCode()).orElse(themeCode),
              selected.stream().flatMap(entry -> entry.items().stream()).limit(MAX_KEYWORDS)
                  .map(PublishedKeyword::keyword).toList());
        })
        .orElseGet(() -> new NaverSeasonKeywords(null, List.of(), null, List.of()));
  }

  public NaverCoupangComparison compare(Integer themeCode) {
    return latest(true).map(publication -> compare(publication, themeCode))
        .orElseGet(() -> new NaverCoupangComparison(null, null, null, List.of(), List.of(), NO_NAVER));
  }

  private NaverCoupangComparison compare(Publication publication, Integer themeCode) {
    var selected = select(themes(publication), themeCode);
    Integer selectedCode = selected.map(entry -> entry.theme().themeCode()).orElse(themeCode);
    String themeName = selected.map(entry -> entry.theme().themeName()).orElse(null);
    var coupang = publication.comparison();
    if (coupang == null || !publication.snapshotDate().equals(coupang.snapshotDate())) {
      return new NaverCoupangComparison(publication.publishedAt(), selectedCode, themeName, List.of(), List.of(), NO_COUPANG);
    }

    var matched = new ArrayList<ComparedKeyword>();
    var unmatched = new ArrayList<ComparedKeyword>();
    for (var item : selected.stream().flatMap(entry -> entry.items().stream()).limit(MAX_KEYWORDS).toList()) {
      Long count = item.matchedProductCount();
      if (count == null || count < 0) throw new BusinessException(NAVER_SEASON_UNAVAILABLE);
      var keyword = item.keyword();
      var result = new ComparedKeyword(keyword.keyword(), keyword.monthlySearchCount(), keyword.growthRate(), count);
      (count > 0 ? matched : unmatched).add(result);
    }
    var updatedAt = publication.publishedAt().isBefore(coupang.publishedAt())
        ? coupang.publishedAt() : publication.publishedAt();
    return new NaverCoupangComparison(updatedAt, selectedCode, themeName, List.copyOf(matched), List.copyOf(unmatched), SCOPE);
  }

  private Optional<Publication> latest(boolean includeComparison) {
    var reader = readers.getIfAvailable();
    if (reader == null) throw new BusinessException(NAVER_SEASON_UNAVAILABLE);
    return reader.latest(includeComparison);
  }

  private static List<PublishedTheme> themes(Publication publication) {
    return publication.themes().stream().sorted(comparingInt(entry -> entry.theme().themeCode())).toList();
  }

  private static Optional<PublishedTheme> select(List<PublishedTheme> themes, Integer themeCode) {
    return themes.stream().filter(entry -> themeCode == null || entry.theme().themeCode() == themeCode).findFirst();
  }
}
