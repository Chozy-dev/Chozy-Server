package com.chozy.server.support;

import com.chozy.server.screening.dto.Screenings.CompetitionLevel;
import com.chozy.server.screening.dto.Screenings.Daily;
import com.chozy.server.screening.service.ScreeningDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ScreeningDataFixture {
  @Bean
  ScreeningDataProvider screeningDataProvider() {
    return new ScreeningDataProvider() {
      @Override
      public SearchBatch search(List<String> keywords, LocalDate endDate) {
        Map<String, SearchData> results = keywords.stream().filter(keyword -> !keyword.equals("missing"))
            .collect(Collectors.toMap(keyword -> keyword, keyword -> new SearchData(
                new AdMetrics(10_000, 20_000, false, BigDecimal.valueOf(2), BigDecimal.valueOf(5),
                    keyword.equals("top") ? CompetitionLevel.LOW : CompetitionLevel.HIGH, BigDecimal.valueOf(4.2)),
                keyword.equals("no-trend") ? List.of() : daily(endDate, 55, 50))));
        return new SearchBatch(results, keywords.contains("no-trend") ? "검색 추이를 조회하지 못했습니다." : null);
      }

      @Override
      public List<Daily> shopping(String keyword, String categoryCode, LocalDate endDate) {
        return categoryCode.equals("50005542") ? List.of() : daily(endDate, 70, 50);
      }
    };
  }

  public static List<Daily> daily(LocalDate end, int current, int prior) {
    return Stream.concat(window(end, current), window(end.minusYears(1), prior)).toList();
  }

  private static Stream<Daily> window(LocalDate end, int value) {
    return end.minusDays(27).datesUntil(end.plusDays(1))
        .map(date -> new Daily(date, BigDecimal.valueOf(value)));
  }
}
