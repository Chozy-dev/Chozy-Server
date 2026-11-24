package com.chozy.server.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class MarketTrends {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private MarketTrends() {}

  static void describe(ObjectNode external, LocalDate today, JsonNode dailyData, JsonNode monthlyData) {
    var daily = points(dailyData, today.minusDays(56), today.minusDays(1));
    var currentMonth = YearMonth.from(today);
    var monthly = points(monthlyData, LocalDate.of(2016, 1, 1), currentMonth.atDay(1).minusDays(1));
    var mid = today.minusDays(28);
    var growth = growth(completeTotal(daily, mid), completeTotal(daily, today.minusDays(56)));
    if (growth != null) external.put("recent4WeeksGrowthRate", growth).put("trendScore", trendScore(growth));
    external.put("lastMonthGrowthRate", growth(monthly.get(currentMonth.minusMonths(1).atDay(1)),
        monthly.get(currentMonth.minusMonths(2).atDay(1))));

    var chart = external.putObject("searchTrend");
    var days = chart.putArray("last30Days");
    daily.tailMap(today.minusDays(30)).forEach((date, value) ->
        days.addObject().put("date", date.toString()).put("value", value));
    var months = chart.putArray("last12Months");
    monthly.tailMap(currentMonth.minusMonths(12).atDay(1)).forEach((date, value) ->
        months.addObject().put("month", YearMonth.from(date).toString()).put("value", value));

    var pattern = new LinkedHashMap<Integer, BigDecimal>();
    var seasonal = chart.putArray("seasonalPattern");
    for (int month = 1; month <= 12; month++) {
      var samples = new ArrayList<BigDecimal>();
      for (var point : monthly.entrySet()) {
        if (point.getKey().getDayOfMonth() == 1 && point.getKey().getMonthValue() == month)
          samples.add(point.getValue());
      }
      if (!samples.isEmpty()) {
        var average = samples.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(samples.size()), 6, RoundingMode.HALF_UP);
        pattern.put(month, average);
        seasonal.addObject().put("month", month).put("value", average);
      }
    }
    if (pattern.size() == 12) describeSeason(external, today, pattern);
  }

  private static TreeMap<LocalDate, BigDecimal> points(JsonNode data, LocalDate start, LocalDate end) {
    var values = new TreeMap<LocalDate, BigDecimal>();
    for (var item : data) {
      var date = LocalDate.parse(item.path("period").asText());
      var ratio = item.path("ratio");
      if (!ratio.isNumber() || ratio.decimalValue().signum() < 0 || ratio.decimalValue().compareTo(HUNDRED) > 0
          || date.isBefore(start) || date.isAfter(end) || values.putIfAbsent(date, ratio.decimalValue()) != null)
        throw new IllegalStateException("Invalid DataLab observations");
    }
    return values;
  }

  private static BigDecimal completeTotal(Map<LocalDate, BigDecimal> values, LocalDate start) {
    var sum = BigDecimal.ZERO;
    for (int offset = 0; offset < 28; offset++) {
      var value = values.get(start.plusDays(offset));
      if (value == null) return null;
      sum = sum.add(value);
    }
    return sum;
  }

  private static BigDecimal growth(BigDecimal recent, BigDecimal previous) {
    return recent == null || previous == null || previous.signum() == 0 ? null
        : recent.subtract(previous).multiply(HUNDRED).divide(previous, 6, RoundingMode.HALF_UP);
  }

  private static int trendScore(BigDecimal growth) {
    if (growth.compareTo(BigDecimal.valueOf(50)) >= 0) return 95;
    if (growth.compareTo(BigDecimal.valueOf(20)) >= 0) return 85;
    if (growth.compareTo(BigDecimal.valueOf(5)) >= 0) return 70;
    if (growth.compareTo(BigDecimal.valueOf(-5)) > 0) return 55;
    if (growth.compareTo(BigDecimal.valueOf(-20)) > 0) return 40;
    return 25;
  }

  private static void describeSeason(ObjectNode external, LocalDate today, Map<Integer, BigDecimal> pattern) {
    var max = Collections.max(pattern.values());
    if (max.signum() == 0) return;
    var season = external.putObject("seasonality");
    if (Collections.min(pattern.values()).compareTo(max.multiply(new BigDecimal("0.6"))) >= 0) {
      season.put("type", "YEAR_ROUND").put("description", "연중 검색 관심이 고르게 나타나요.");
      external.put("seasonalityScore", 70);
      return;
    }
    var threshold = max.multiply(new BigDecimal("0.8"));
    int start = pattern.entrySet().stream().filter(point -> point.getValue().compareTo(max) == 0)
        .findFirst().orElseThrow().getKey();
    int length = 1;
    while (pattern.get(wrap(start - 1)).compareTo(threshold) >= 0) {
      start = wrap(start - 1);
      length++;
    }
    while (pattern.get(wrap(start + length)).compareTo(threshold) >= 0) length++;
    var peak = season.putArray("peakMonths");
    for (int offset = 0; offset < length; offset++) peak.add(wrap(start + offset));
    season.put("description", "과거 월별 검색 관심도가 높았던 시기를 보여줘요.");
    var from = LocalDate.of(today.getYear(), start, 1);
    if (from.isAfter(today)) from = from.minusYears(1);
    var end = from.plusMonths(length);
    if (today.isBefore(end)) {
      long weeks = remainingWeeks(today, end);
      season.put("type", "PEAK_SEASON").put("weeksRemainingInPeak", weeks);
      external.put("seasonalityScore", weeks >= 6 ? 85 : 60);
    } else {
      long weeks = remainingWeeks(today, from.plusYears(1));
      season.put("type", "OFF_SEASON").put("weeksUntilPeak", weeks);
      external.put("seasonalityScore", weeks <= 8 ? 75 : weeks <= 20 ? 55 : 40);
    }
  }

  private static int wrap(int month) {
    return Math.floorMod(month - 1, 12) + 1;
  }

  private static long remainingWeeks(LocalDate today, LocalDate boundary) {
    return Math.ceilDiv(ChronoUnit.DAYS.between(today, boundary), 7);
  }
}
