package com.chozy.server.screening.service;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import com.chozy.server.screening.dto.Screenings.*;
import com.chozy.server.screening.service.ScreeningDataProvider.SearchData;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

final class ScreeningScoring {
  private static final MathContext PRECISION = MathContext.DECIMAL128;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal GROWTH_THRESHOLD = BigDecimal.TEN; // Planning document provisional threshold.
  private static final Quadrant GOLDEN = new Quadrant(QuadrantType.GOLDEN_TIME, "지금이 골든타임",
      "관심도 늘고 구매도 늘어 들어가기 좋은 때입니다.");
  private static final Quadrant BUZZ = new Quadrant(QuadrantType.BUZZ_ONLY, "반짝 화제, 아직 안 팔려요",
      "광고비를 쓰기 전에 며칠 더 지켜보세요.");
  private static final Quadrant HIDDEN = new Quadrant(QuadrantType.HIDDEN_DEMAND, "소리 없이 잘 팔리는 시장",
      "화제성은 없지만 구매 수요가 꾸준히 늘고 있습니다.");
  private static final Quadrant DECLINING = new Quadrant(QuadrantType.DECLINING, "관심이 식어가는 중",
      "지금은 우선순위를 낮춰 보세요.");

  private ScreeningScoring() {}

  static Keyword keyword(String keyword, SearchData data, LocalDate end) {
    var ads = data.ads();
    long count = Math.addExact(ads.pcSearchCount(), ads.mobileSearchCount());
    var pc = BigDecimal.valueOf(ads.pcSearchCount());
    var mobile = BigDecimal.valueOf(ads.mobileSearchCount());
    var weightedClicks = pc.multiply(ads.pcClickRate()).add(mobile.multiply(ads.mobileClickRate()));
    var ctr = count == 0 ? ZERO : weightedClicks.divide(BigDecimal.valueOf(count), PRECISION);
    var mobileRatio = count == 0 ? ZERO : mobile.multiply(HUNDRED).divide(BigDecimal.valueOf(count), PRECISION);
    var series = project(data.searchTrend(), end);
    int competition = ads.competitionLevel().score();
    return new Keyword(keyword, score(count, ctr, competition, series.growth(), null),
        count, ads.searchCountEstimated(), ctr, mobileRatio.setScale(2, HALF_UP), ads.competitionLevel(),
        competition, ads.averageAdDepth(), series.growth(), null, null, null, null, series.trend() != null,
        series.trend());
  }

  static ShoppingResult shopping(Keyword keyword, Category category, List<Daily> daily, LocalDate end) {
    var series = project(daily, end);
    var search = keyword.searchGrowthRate();
    var shopping = series.growth();
    int score = score(keyword.monthlySearchCount(), keyword.clickThroughRate(), keyword.competitionScore(),
        search, shopping);
    return new ShoppingResult(keyword.keyword(), category.categoryCode(), category.categoryName(), score,
        search, shopping, quadrant(search, shopping), series.trend());
  }

  private static int score(long volume, BigDecimal ctr, int competition, BigDecimal search, BigDecimal shopping) {
    // Provisional weights from planning section 4.4. Missing axes are removed from the denominator.
    var demand = BigDecimal.valueOf(Math.min(volume, 30_000)).multiply(BigDecimal.valueOf(25))
        .divide(BigDecimal.valueOf(30_000), PRECISION)
        .add(ctr.min(BigDecimal.valueOf(8)).multiply(BigDecimal.valueOf(15))
            .divide(BigDecimal.valueOf(8), PRECISION));
    var weighted = demand.add(BigDecimal.valueOf(100L - competition).multiply(BigDecimal.valueOf(25))
        .divide(HUNDRED, PRECISION));
    int available = 65;
    if (search != null) {
      weighted = weighted.add(growthPoints(search, 25));
      available += 25;
    }
    if (shopping != null) {
      weighted = weighted.add(growthPoints(shopping, 10));
      available += 10;
    }
    return weighted.multiply(HUNDRED).divide(BigDecimal.valueOf(available), 0, HALF_UP).intValueExact();
  }

  private static BigDecimal growthPoints(BigDecimal growth, int weight) {
    var half = BigDecimal.valueOf(weight).divide(BigDecimal.valueOf(2));
    return half.add(half.multiply(growth.movePointLeft(2))).max(ZERO).min(BigDecimal.valueOf(weight));
  }

  private static Quadrant quadrant(BigDecimal search, BigDecimal shopping) {
    if (search == null || shopping == null) return null;
    boolean searchUp = search.compareTo(GROWTH_THRESHOLD) >= 0;
    boolean shoppingUp = shopping.compareTo(GROWTH_THRESHOLD) >= 0;
    if (searchUp) return shoppingUp ? GOLDEN : BUZZ;
    return shoppingUp ? HIDDEN : DECLINING;
  }

  private static Series project(List<Daily> daily, LocalDate end) {
    var values = new TreeMap<LocalDate, BigDecimal>();
    // The provider reports one observation per date within the requested series.
    for (var point : daily) {
      if (!point.date().isAfter(end)) values.put(point.date(), point.value());
    }
    if (values.isEmpty()) return new Series(null, null);
    // Both complete windows have 28 days. Comparing sums avoids rounding the averages before taking their ratio.
    var thisYear = fourWeekTotal(values, end);
    var priorYear = fourWeekTotal(values, end.minusYears(1));
    var growth = thisYear == null || priorYear == null || priorYear.signum() == 0 ? null
        : thisYear.subtract(priorYear).multiply(HUNDRED).divide(priorYear, PRECISION);

    var recent = values.tailMap(end.minusDays(29), true).entrySet().stream()
        .map(point -> new Daily(point.getKey(), point.getValue())).toList();
    var months = new TreeMap<YearMonth, List<BigDecimal>>();
    var firstMonth = YearMonth.from(end).minusMonths(11).atDay(1);
    values.tailMap(firstMonth, true).forEach((date, value) ->
        months.computeIfAbsent(YearMonth.from(date), ignored -> new ArrayList<>()).add(value));
    var monthly = months.entrySet().stream()
        .map(month -> new Monthly(month.getKey(), mean(month.getValue()).setScale(2, HALF_UP))).toList();
    Trend trend = recent.isEmpty() && monthly.isEmpty() ? null : new Trend(recent, monthly);
    return new Series(growth, trend);
  }

  private static BigDecimal fourWeekTotal(NavigableMap<LocalDate, BigDecimal> values, LocalDate end) {
    var window = values.subMap(end.minusDays(27), true, end, true);
    return window.size() == 28 ? sum(window.values()) : null;
  }

  private static BigDecimal mean(Collection<BigDecimal> values) {
    return sum(values).divide(BigDecimal.valueOf(values.size()), PRECISION);
  }

  private static BigDecimal sum(Collection<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  private record Series(BigDecimal growth, Trend trend) {}
}
