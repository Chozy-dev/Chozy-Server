package com.chozy.server.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.integration.external.MarketApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MarketManualAnalysisEngineTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T02:00:00Z"), ZoneOffset.UTC);
  private static final ManualAnalysisInput INPUT = new ManualAnalysisInput("캠핑 랜턴 5000mAh",
      BigDecimal.valueOf(8000), BigDecimal.valueOf(3000), false, null, "캠핑 랜턴");
  private static final String TREND_URL = "https://naverapihub.apigw.ntruss.com/search-trend/v1/search";
  private static final String COUPANG_URL =
      "https://api-gateway.coupang.com/v2/providers/affiliate_open_api/apis/openapi/products/search"
          + "?keyword=%EC%BA%A0%ED%95%91%20%EB%9E%9C%ED%84%B4%205000mAh&limit=10";

  private final ObjectMapper mapper = new ObjectMapper();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final RestClient.Builder http = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
  private final MarketManualAnalysisEngine engine = new MarketManualAnalysisEngine(
      new MarketApiClient(http.build(), mapper, jdbc, CLOCK, "coupang", "naver", "access", "secret",
          "ad-key", "ad-secret", "customer", "hub-id", "hub-key"), mapper, CLOCK);

  @Test
  void createsMarketSnapshotAndCachesSuccessfulLookups() {
    expectMarketData();
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("trend")))
        .thenReturn("reserved");
    var monthly = mapper.createArrayNode();
    for (int month = 1; month <= 12; month++) {
      int ratio = month == 12 || month <= 2 ? 100 : 20;
      monthly.addObject().put("period", LocalDate.of(2025, month, 1).toString()).put("ratio", ratio);
    }
    var daily = mapper.createArrayNode();
    for (int day = 56; day >= 1; day--) {
      daily.addObject().put("period", LocalDate.of(2026, 11, 24).minusDays(day).toString())
          .put("ratio", day > 28 ? 40 : 60);
    }
    server.expect(requestTo(TREND_URL))
        .andRespond(withSuccess("{\"results\":[{\"data\":" + monthly + "}]}", MediaType.TEXT_PLAIN));
    server.expect(requestTo(TREND_URL))
        .andRespond(withSuccess("{\"results\":[{\"data\":" + daily + "}]}", MediaType.APPLICATION_OCTET_STREAM));

    var result = engine.analyze(1, INPUT);
    var snapshot = result.snapshot();
    assertThat(result.dataLabQuotaExhausted()).isFalse();
    assertThat(snapshot.at("/internalDiagnosis/profitability/marketPrice").decimalValue())
        .isEqualByComparingTo("22000");
    assertThat(snapshot.at("/investmentStrategy/priceCompetitiveness/excludedBundleCount").intValue()).isEqualTo(1);
    assertThat(snapshot.at("/externalDiagnosis/trendScore").intValue()).isEqualTo(95);
    assertThat(snapshot.at("/externalDiagnosis/seasonality/peakMonths").toString()).isEqualTo("[12,1,2]");
    assertThat(snapshot.at("/externalDiagnosis/seasonality/type").textValue()).isEqualTo("OFF_SEASON");
    assertThat(snapshot.at("/summary/scoreStatus").textValue()).isEqualTo("WAITING_FOR_INPUT");
    assertThat(snapshot.at("/summary/requiredInputs").toString()).isEqualTo("[\"COUPANG_CATEGORY\"]");
    assertThat(engine.analyze(2, INPUT).snapshot()).isEqualTo(snapshot);
    verify(jdbc, times(4)).queryForObject(startsWith("SELECT CASE"), eq(String.class), anyString(), anyString());
    server.verify();
  }

  @Test
  void exemptsOnlyDataLabDailyExhaustion() {
    expectMarketData();
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("trend")))
        .thenReturn("exhausted:day");
    var result = engine.analyze(1, INPUT);
    assertThat(result.dataLabQuotaExhausted()).isTrue();
    assertThat(result.snapshot().at("/unavailableFeatures/0").textValue()).isEqualTo("MARKET_TREND");
    assertThat(result.snapshot().at("/externalDiagnosis/searchTrend").isMissingNode()).isTrue();

    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("trend")))
        .thenReturn("blocked");
    assertThatThrownBy(() -> engine.analyze(2, INPUT)).isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  void providerRateLimitBlocksSharedPolicyAndFailsAnalysis() {
    expectMarketData();
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("trend")))
        .thenReturn("reserved");
    server.expect(requestTo(TREND_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    var steps = new ArrayList<Integer>();
    assertThatThrownBy(() -> engine.analyze(1, INPUT, steps::add)).isInstanceOf(IllegalStateException.class);
    assertThat(steps).containsExactly(4, 5);
    verify(jdbc).queryForObject("SELECT public.block_api_account(?, ?)", String.class, "naver", "trend");
    server.verify();
  }

  @ParameterizedTest
  @EnumSource(value = HttpStatus.class, names = {"SERVICE_UNAVAILABLE", "TOO_MANY_REQUESTS"})
  void preservesReportWithManualPriceInputWhenCoupangSearchFails(HttpStatus status) {
    expectKeywordData();
    server.expect(requestTo(COUPANG_URL)).andRespond(withStatus(status));
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("trend")))
        .thenReturn("reserved");
    server.expect(requestTo(TREND_URL))
        .andRespond(withSuccess("{\"results\":[{\"data\":[]}]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(TREND_URL))
        .andRespond(withSuccess("{\"results\":[{\"data\":[]}]}", MediaType.APPLICATION_JSON));

    var result = engine.analyze(1, INPUT);
    assertThat(result.dataLabQuotaExhausted()).isFalse();
    assertThat(result.snapshot().at("/internalDiagnosis/profitability/marketPrice").isNull()).isTrue();
    assertThat(result.snapshot().at("/summary/scoreStatus").textValue()).isEqualTo("WAITING_FOR_INPUT");
    assertThat(result.snapshot().at("/summary/requiredInputs").toString())
        .isEqualTo("[\"EXPECTED_SELLING_PRICE\",\"COUPANG_CATEGORY\"]");
    assertThat(result.snapshot().at("/externalDiagnosis/competitionScore").intValue()).isEqualTo(80);
    if (status == HttpStatus.TOO_MANY_REQUESTS) {
      verify(jdbc).queryForObject("SELECT public.block_api_account(?, ?)", String.class, "coupang", "default");
    }
    server.verify();
  }

  @Test
  void rejectsAnalysisBeforeCoupangRequestWhenBudgetGuardIsUnavailable() {
    expectKeywordData();
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("coupang"), eq("default")))
        .thenReturn("runtime_budget_guard_unavailable");

    assertThatThrownBy(() -> engine.analyze(1, INPUT))
        .isInstanceOf(IllegalStateException.class).hasMessage("Market call budget unavailable");
    server.verify();
  }

  private void expectKeywordData() {
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("naver"), eq("ad")))
        .thenReturn("reserved");
    when(jdbc.queryForObject(startsWith("SELECT CASE"), eq(String.class), eq("coupang"), eq("default")))
        .thenReturn("reserved");
    server.expect(requestTo("https://api.searchad.naver.com/keywordstool"
            + "?hintKeywords=%EC%BA%A0%ED%95%91%EB%9E%9C%ED%84%B4&includeHintKeywords=1&showDetail=1"))
        .andExpect(header("X-Customer", "customer"))
        .andRespond(withSuccess("""
            {"keywordList":[{"relKeyword":"캠핑랜턴","monthlyPcQcCnt":500,"monthlyMobileQcCnt":1100,"compIdx":"낮음"}]}
            """, MediaType.APPLICATION_JSON));
  }

  private void expectMarketData() {
    expectKeywordData();
    server.expect(requestTo(COUPANG_URL))
        .andRespond(withSuccess("""
            {"rCode":"0","data":{"productData":[
              {"productId":1,"productName":"랜턴 5,000mAh","productPrice":20000,"isRocket":false,"isFreeShipping":false},
              {"productId":2,"productName":"랜턴 5000mAh","productPrice":21000,"isRocket":true,"isFreeShipping":true},
              {"productId":3,"productName":"랜턴 5000mAh 2개 세트","productPrice":19000,"isRocket":true},
              {"productId":4,"productName":"랜턴 2000mAh","productPrice":9000,"isRocket":false}
            ]}}
            """, MediaType.APPLICATION_JSON));
  }
}
