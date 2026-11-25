package com.chozy.server.integration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class MarketApiClient {
  private static final String COUPANG_SEARCH = "/v2/providers/affiliate_open_api/apis/openapi/products/search";
  private static final String SEARCH_AD = "/keywordstool";
  private static final DateTimeFormatter COUPANG_DATE =
      DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final Duration CACHE_TTL = Duration.ofMinutes(10);
  private static final int CACHE_LIMIT = 256;

  private final RestClient http;
  private final JdbcTemplate budgets;
  private final Clock clock;
  private final String coupangAccount;
  private final String naverAccount;
  private final String coupangAccess;
  private final String coupangSecret;
  private final String adKey;
  private final String adSecret;
  private final String customer;
  private final String hubId;
  private final String hubKey;
  private final LinkedHashMap<String, CachedResponse> cache = new LinkedHashMap<>(16, 0.75f, true);

  public MarketApiClient(RestClient http, JdbcTemplate budgets, Clock clock, String coupangAccount, String naverAccount,
      String coupangAccess, String coupangSecret, String adKey, String adSecret, String customer,
      String hubId, String hubKey) {
    this.http = http;
    this.budgets = budgets;
    this.clock = clock;
    this.coupangAccount = coupangAccount;
    this.naverAccount = naverAccount;
    this.coupangAccess = coupangAccess;
    this.coupangSecret = coupangSecret;
    this.adKey = adKey;
    this.adSecret = adSecret;
    this.customer = customer;
    this.hubId = hubId;
    this.hubKey = hubKey;
  }

  public JsonNode products(String keyword) {
    return cached("products:" + keyword, () -> {
      reserve(coupangAccount, "default");
      String query = "keyword=" + encode(keyword) + "&limit=10";
      String date = COUPANG_DATE.format(clock.instant());
      String signature = HexFormat.of().formatHex(sign(coupangSecret, date + "GET" + COUPANG_SEARCH + query));
      String auth = "CEA algorithm=HmacSHA256,access-key=" + coupangAccess + ",signed-date=" + date
          + ",signature=" + signature;
      try {
        var response = request(http.get().uri(URI.create("https://api-gateway.coupang.com" + COUPANG_SEARCH + "?" + query))
            .header("Authorization", auth), coupangAccount, "default");
        String code = response.path("rCode").asText();
        if ("403".equals(code) || "429".equals(code)) block(coupangAccount, "default");
        if (!"0".equals(code)) throw new ProviderResponseException("Coupang search response failed");
        return requireArray(response.path("data").path("productData"));
      } catch (ProviderResponseException | RestClientException ex) {
        throw new CoupangSearchUnavailable(ex);
      }
    });
  }

  public JsonNode keywords(String keyword) {
    String hint = keyword.replaceAll("(?U)[\\s,]+", "");
    if (hint.isEmpty()) return JsonNodeFactory.instance.arrayNode();
    return cached("keywords:" + hint, () -> {
      reserve(naverAccount, "ad");
      String timestamp = Long.toString(clock.millis());
      String signature = Base64.getEncoder().encodeToString(sign(adSecret, timestamp + ".GET." + SEARCH_AD));
      var uri = URI.create("https://api.searchad.naver.com" + SEARCH_AD + "?hintKeywords=" + encode(hint)
          + "&includeHintKeywords=1&showDetail=1");
      var response = request(http.get().uri(uri).header("X-Timestamp", timestamp).header("X-API-KEY", adKey)
          .header("X-Customer", customer).header("X-Signature", signature), naverAccount, "ad");
      return requireArray(response.path("keywordList"));
    });
  }

  public JsonNode trend(String keyword, LocalDate start, LocalDate end, String unit) {
    return cached("trend:" + unit + ":" + start + ":" + end + ":" + keyword, () -> {
      reserve(naverAccount, "trend");
      var payload = Map.of("startDate", start.toString(), "endDate", end.toString(), "timeUnit", unit,
          "keywordGroups", List.of(Map.of("groupName", keyword, "keywords", List.of(keyword))));
      var response = request(http.post().uri("https://naverapihub.apigw.ntruss.com/search-trend/v1/search")
          .header("X-NCP-APIGW-API-KEY-ID", hubId).header("X-NCP-APIGW-API-KEY", hubKey)
          .contentType(MediaType.APPLICATION_JSON).body(payload), naverAccount, "trend");
      var results = requireArray(response.path("results"));
      if (results.size() != 1) throw new IllegalStateException("Invalid DataLab group response");
      return requireArray(results.get(0).path("data"));
    });
  }

  private JsonNode request(RestClient.RequestHeadersSpec<?> request, String account, String api) {
    return request.exchange((sent, response) -> {
      int status = response.getStatusCode().value();
      if (status == 403 || status == 429) block(account, api);
      if (status != 200) throw new ProviderResponseException("Market API HTTP status " + status);
      var body = response.bodyTo(JsonNode.class);
      if (body == null || !body.isObject()) throw new ProviderResponseException("Invalid market API response");
      return body;
    });
  }

  private void reserve(String account, String api) {
    String result = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      if (attempt > 0) {
        try {
          Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Market reservation interrupted", ex);
        }
      }
      result = budgets.queryForObject("""
          SELECT CASE WHEN to_regprocedure('public.block_api_account(text,text)') IS NULL
              THEN 'runtime_budget_guard_missing'
              ELSE public.reserve_api_call(?, ?, true) END
          """, String.class, account, api);
      if (!"interval".equals(result)) break;
    }
    if ("trend".equals(api) && "exhausted:day".equals(result)) throw new DataLabDailyLimit();
    if (!"reserved".equals(result)) throw new IllegalStateException("Market call budget unavailable");
  }

  private void block(String account, String api) {
    budgets.queryForObject("SELECT public.block_api_account(?, ?)", String.class, account, api);
    throw new ProviderResponseException("Market API access restricted");
  }

  private JsonNode cached(String key, Supplier<JsonNode> loader) {
    synchronized (cache) {
      var old = cache.get(key);
      if (old != null && old.expiresAt().isAfter(clock.instant())) return old.body();
    }
    var body = loader.get();
    synchronized (cache) {
      cache.put(key, new CachedResponse(body, clock.instant().plus(CACHE_TTL)));
      if (cache.size() > CACHE_LIMIT) cache.pollFirstEntry();
    }
    return body;
  }

  private static JsonNode requireArray(JsonNode node) {
    if (!node.isArray()) throw new ProviderResponseException("Invalid market API response data");
    return node;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static byte[] sign(String secret, String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Cannot sign market API request", ex);
    }
  }

  private record CachedResponse(JsonNode body, Instant expiresAt) {}

  private static final class ProviderResponseException extends IllegalStateException {
    ProviderResponseException(String message) {
      super(message);
    }
  }

  public static final class CoupangSearchUnavailable extends IllegalStateException {
    private CoupangSearchUnavailable(RuntimeException cause) {
      super("Coupang search is unavailable", cause);
    }
  }

  public static final class DataLabDailyLimit extends RuntimeException {}
}
