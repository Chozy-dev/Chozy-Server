package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.integration.external.MarketApiClient;
import com.chozy.server.report.service.ReportRecalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

final class MarketManualAnalysisEngine implements ManualAnalysisEngine {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final int ANALYSIS_KEYWORD_STEP = 4;
  private static final int MARKET_DATA_STEP = 5;
  private final MarketApiClient client;
  private final ObjectMapper mapper;
  private final Clock clock;

  MarketManualAnalysisEngine(MarketApiClient client, ObjectMapper mapper, Clock clock) {
    this.client = client;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public Result analyze(long analysisId, ManualAnalysisInput input) {
    return analyze(analysisId, input, step -> {});
  }

  @Override
  public Result analyze(long analysisId, ManualAnalysisInput input, IntConsumer progress) {
    var report = mapper.createObjectNode();
    var summary = report.putObject("summary");
    var external = report.putObject("externalDiagnosis");
    var strategy = report.putObject("investmentStrategy");
    var profit = report.putObject("internalDiagnosis").putObject("profitability");
    String original = input.keyword() == null ? input.productName() : input.keyword();
    progress.accept(ANALYSIS_KEYWORD_STEP);
    var keywords = keywords(client.keywords(original));
    var selected = select(original, input.productName(), keywords);
    String keyword = selected == null ? original : selected.name();
    external.put("originalKeyword", original).put("keyword", keyword);
    if (!normalize(original).equals(normalize(keyword))) {
      external.put("keywordChangeReason", "원래 키워드의 검색 데이터가 부족해 상품명과 관련된 키워드로 분석했어요.");
    }
    if (selected != null) {
      external.put("monthlySearchCount", selected.volume()).put("competitionLevel", selected.competition())
          .put("averageAdDepth", selected.adDepth()).put("isSearchCountEstimated", false);
    }

    progress.accept(MARKET_DATA_STEP);
    String marketQuery = input.productName();
    JsonNode rows;
    boolean searchFailed = false;
    try {
      rows = client.products(marketQuery);
      if (rows.isEmpty() && !normalize(marketQuery).equals(normalize(keyword))) {
        marketQuery = keyword;
        rows = client.products(marketQuery);
      }
    } catch (MarketApiClient.CoupangSearchUnavailable ex) {
      searchFailed = true;
      rows = mapper.createArrayNode();
    }
    var market = CoupangMarketPrice.analyze(rows, input.productName(), input.shippingFee());
    var priceCompetitiveness = strategy.putObject("priceCompetitiveness");
    market.describe(priceCompetitiveness, marketQuery);
    if (searchFailed) {
      priceCompetitiveness.put("notice", "쿠팡에서 시장가를 조회하지 못했어요. 예상 판매가를 입력해 주세요.");
    }
    profit.put("marketPrice", market.marketPrice());
    var rocketRatio = market.comparisonCount() == 0 ? null
        : BigDecimal.valueOf(100L * market.rocketCount())
            .divide(BigDecimal.valueOf(market.comparisonCount()), 4, RoundingMode.HALF_UP);
    external.put("comparisonProductCount", market.comparisonCount()).put("rocketProductCount", market.rocketCount())
        .put("rocketDeliveryRatio", rocketRatio);
    var competition = selected == null ? null : selected.competition();
    if (competition != null) {
      int base = switch (competition) {
        case "LOW" -> 80;
        case "MEDIUM" -> 50;
        default -> 20;
      };
      boolean highRocketShare = market.rocketCount() * 10 >= market.comparisonCount() * 7
          && market.comparisonCount() > 0;
      external.put("competitionScore", highRocketShare ? base - 10 : base);
    }

    boolean exempt = false;
    var features = report.putArray("unavailableFeatures");
    try {
      var today = LocalDate.now(clock.withZone(SEOUL));
      var monthStart = today.withDayOfMonth(1);
      var monthly = client.trend(keyword, LocalDate.of(2016, 1, 1), monthStart.minusDays(1), "month");
      var daily = client.trend(keyword, today.minusDays(56), today.minusDays(1), "date");
      MarketTrends.describe(external, today, daily, monthly);
    } catch (MarketApiClient.DataLabDailyLimit ex) {
      exempt = true;
      features.add("MARKET_TREND");
      external.put("notice", "데이터랩의 일일 호출 한도를 모두 사용해 트렌드와 지속성을 제외했어요.");
    }
    strategy.put("conclusion", "쿠팡 대분류를 선택하면 기준 마진과 투자 등급을 계산할 수 있어요.");
    strategy.putObject("coupangRegistration").put("notice", "쿠팡 대분류를 선택해 주세요.");
    strategy.putArray("platformComparison").addObject().put("platform", "COUPANG")
        .put("rocketProductCount", market.rocketCount());
    summary.put("description", market.marketPrice() == null
        ? "예상 판매가와 쿠팡 대분류를 입력하면 종합점수를 계산할 수 있어요."
        : "쿠팡 대분류를 선택하면 시장가 기준으로 종합점수를 계산할 수 있어요.");
    ReportRecalculator.initializeManual(report, input);
    return new Result(report, exempt);
  }

  private static List<Keyword> keywords(JsonNode rows) {
    var keywords = new ArrayList<Keyword>();
    for (var row : rows) {
      var name = row.path("relKeyword").asText("");
      if (name.isBlank()) continue;
      var pc = searchVolume(row.path("monthlyPcQcCnt"));
      var mobile = searchVolume(row.path("monthlyMobileQcCnt"));
      var volume = pc == null || mobile == null ? null : pc.add(mobile);
      String competition = switch (row.path("compIdx").asText("")) {
        case "낮음" -> "LOW";
        case "중간" -> "MEDIUM";
        case "높음" -> "HIGH";
        default -> null;
      };
      var depth = row.path("plAvgDepth");
      keywords.add(new Keyword(name, volume, competition, searchVolume(depth)));
    }
    return keywords;
  }

  private static BigDecimal searchVolume(JsonNode node) {
    var text = node.asText("").strip();
    if (!text.matches("[0-9]+(?:\\.[0-9]+)?")) return null;
    return new BigDecimal(text);
  }

  private static Keyword select(String original, String productName, List<Keyword> rows) {
    String hint = normalize(original);
    var exact = rows.stream().filter(row -> normalize(row.name()).equals(hint)).findFirst().orElse(null);
    if (exact != null && exact.enough()) return exact;
    var tokens = Arrays.stream(productName.split("(?U)[^\\p{L}\\p{N}]+"))
        .map(MarketManualAnalysisEngine::normalize).filter(word -> word.length() > 1).distinct().toList();
    var related = rows.stream().filter(row -> row.volume() != null
        && tokens.stream().anyMatch(normalize(row.name())::contains)).toList();
    var broad = related.stream().max(Comparator.comparing(Keyword::volume)).orElse(null);
    if (broad == null) return exact;
    var broadName = normalize(broad.name());
    var categoryWords = tokens.stream().filter(broadName::contains).toList();
    var attributes = tokens.stream().filter(word -> !broadName.contains(word)).toList();
    return related.stream().filter(Keyword::enough).filter(row -> {
      var name = normalize(row.name());
      return categoryWords.stream().allMatch(name::contains) && attributes.stream().anyMatch(name::contains);
    }).max(Comparator.<Keyword>comparingLong(row -> attributes.stream().filter(normalize(row.name())::contains).count())
        .thenComparing(Keyword::volume)).orElse(broad);
  }

  private static String normalize(String value) {
    return value.replaceAll("(?U)[\\s,]+", "").toLowerCase(Locale.ROOT);
  }

  private record Keyword(String name, BigDecimal volume, String competition, BigDecimal adDepth) {
    boolean enough() {
      return volume != null && volume.compareTo(BigDecimal.valueOf(1000)) >= 0;
    }
  }
}
