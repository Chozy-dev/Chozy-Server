package com.chozy.server.screening.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chozy.server.screening.dto.Screenings.Category;
import com.chozy.server.screening.dto.Screenings.CompetitionLevel;
import com.chozy.server.screening.dto.Screenings.Daily;
import com.chozy.server.screening.dto.Screenings.QuadrantType;
import com.chozy.server.screening.service.ScreeningDataProvider.AdMetrics;
import com.chozy.server.screening.service.ScreeningDataProvider.SearchData;
import com.chozy.server.support.ScreeningDataFixture;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScreeningScoringTest {
  private static final LocalDate END = LocalDate.of(2026, 11, 22);
  private static final Category CATEGORY = new Category("50000000", "패션의류");
  private static final AdMetrics ADS = new AdMetrics(10_000, 20_000, false, BigDecimal.valueOf(2),
      BigDecimal.valueOf(5), CompetitionLevel.HIGH, null);

  @ParameterizedTest
  @CsvSource({"55,55,GOLDEN_TIME", "55,54,BUZZ_ONLY", "54,55,HIDDEN_DEMAND", "54,54,DECLINING"})
  void bothAxesUseTheInclusiveTenPercentThreshold(int search, int shopping, QuadrantType expected) {
    var keyword = ScreeningScoring.keyword("keyword",
        new SearchData(ADS, ScreeningDataFixture.daily(END, search, 50)), END);
    var result = ScreeningScoring.shopping(keyword, CATEGORY, ScreeningDataFixture.daily(END, shopping, 50), END);

    assertThat(result.quadrant().type()).isEqualTo(expected);
  }

  @Test
  void comparesCompleteWindowsBeforeRoundingTheirMeans() {
    var daily = new ArrayList<>(ScreeningDataFixture.daily(END, 0, 0));
    daily.set(0, new Daily(END.minusDays(27), new BigDecimal("1.1")));
    daily.set(28, new Daily(END.minusYears(1).minusDays(27), BigDecimal.ONE));
    var keyword = ScreeningScoring.keyword("keyword", new SearchData(ADS, daily), END);
    var shopping = ScreeningScoring.shopping(keyword, CATEGORY, daily, END);

    assertThat(keyword.searchGrowthRate()).isEqualByComparingTo("10");
    assertThat(shopping.quadrant().type()).isEqualTo(QuadrantType.GOLDEN_TIME);
  }

  @Test
  void keepsDisplayTrendButRenormalizesScoreWhenThePriorWindowIsZero() {
    var keyword = ScreeningScoring.keyword("keyword", new SearchData(ADS, ScreeningDataFixture.daily(END, 50, 0)), END);
    var result = ScreeningScoring.shopping(keyword, CATEGORY, List.of(), END);

    assertThat(keyword.score()).isEqualTo(58);
    assertThat(keyword.searchGrowthRate()).isNull();
    assertThat(keyword.trendAvailable()).isTrue();
    assertThat(result.score()).isEqualTo(keyword.score());
    assertThat(result.quadrant()).isNull();
  }
}
