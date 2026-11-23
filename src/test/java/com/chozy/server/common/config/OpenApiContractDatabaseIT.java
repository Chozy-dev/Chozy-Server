package com.chozy.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ReportDataFixture;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
  "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "app.security.docs-public=true"
})
class OpenApiContractDatabaseIT extends AccountPostgresITSupport {
  private JsonNode spec;

  @Test
  void rejectsManualAnalysisWhenTheEngineIsAbsent() throws Exception {
    var member = account();
    mvc.perform(post("/search/analyses/manual").header("Authorization", member.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"productName":"Lantern","wholesalePrice":8000,"shippingFee":0,"hasPricePolicy":false}
                """))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("R003"));
    mvc.perform(post("/search/analyses/url").header("Authorization", member.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://domeme.domeggook.com/12345678\"}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("R003"));
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.manual_analysis_jobs WHERE member_id = :id")
        .param("id", member.id()).query(Long.class).single()).isZero();
  }

  @Test
  void rejectsScreeningWhenTheProviderIsAbsent() throws Exception {
    var member = account();
    mvc.perform(post("/search/screenings").header("Authorization", member.bearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"keywords\":[\"lantern\"]}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("S001"));
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.member_screening_runs WHERE member_id = :id")
        .param("id", member.id()).query(Long.class).single()).isZero();
  }

  @Test
  void leavesReportUnchangedWhenKeywordRefreshCannotRun() throws Exception {
    var member = account();
    var data = new ReportDataFixture(jdbc);
    long analysis = data.analysis(member.id(), "Lantern", "COMPLETED", "2026-11-23T01:01:00Z");
    long report = data.report(analysis, "{}");
    mvc.perform(patch("/search/reports/{id}/inputs", report).header("Authorization", member.bearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"keyword\":\"new keyword\",\"cpc\":100}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("R006"));
    assertThat(jdbc.sql("SELECT CAST(snapshot AS text) FROM chozy.manual_analysis_reports WHERE id = :id")
        .param("id", report).query(String.class).single()).isEqualTo("{}");
  }

  @BeforeEach
  void loadOpenApi() throws Exception {
    spec = response(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
  }

  @Test
  void exposesOnlyImplementedOperationsWithTheCorrectBearerRequirements() throws Exception {
    var publicPaths = Map.ofEntries(Map.entry("/auth/signup", "post"), Map.entry("/auth/login", "post"),
        Map.entry("/auth/check-login-id", "get"), Map.entry("/auth/reissue", "post"),
        Map.entry("/search/categories", "get"), Map.entry("/search/products/resolve-url", "post"),
        Map.entry("/search/coupang-categories", "get"), Map.entry("/home/products/recommended", "get"),
        Map.entry("/home/keywords/coupang", "get"), Map.entry("/home/keywords/naver", "get"),
        Map.entry("/home/keywords/compare", "get"));
    var optionalBearer = Map.of("/search/screenings", "post",
        "/search/screenings/{screeningId}/shopping-analysis", "post");
    var protectedPaths = Map.ofEntries(Map.entry("/auth/logout", "post"), Map.entry("/my/dashboard", "get"),
        Map.entry("/my/profile", "patch"), Map.entry("/my/password", "patch"), Map.entry("/my/account", "delete"),
        Map.entry("/my/marketing-consent", "patch"), Map.entry("/home/report-usage", "get"),
        Map.entry("/search/analyses/manual", "post"), Map.entry("/search/analyses/{analysisId}/status", "get"),
        Map.entry("/search/analyses/url", "post"),
        Map.entry("/search/reports/{reportId}", "get"), Map.entry("/search/reports/{reportId}/feedback", "post"),
        Map.entry("/search/reports/{reportId}/inputs", "patch"),
        Map.entry("/search/analyses/recent", "get"), Map.entry("/search/screenings/recent", "get"));
    var favoritesAndNotifications = Map.of(
        "/favorites", Set.of("get", "post"),
        "/favorites/{favoriteId}", Set.of("get", "delete"),
        "/favorites/{favoriteId}/status", Set.of("patch"),
        "/favorites/{favoriteId}/sales-results", Set.of("post"),
        "/home/notifications", Set.of("get"),
        "/home/notifications/{notificationId}/read", Set.of("patch"),
        "/home/notifications/read-all", Set.of("patch"),
        "/my/notification-settings", Set.of("get", "patch"));
    var expectedPaths = new HashSet<>(publicPaths.keySet());
    expectedPaths.addAll(protectedPaths.keySet());
    expectedPaths.addAll(optionalBearer.keySet());
    expectedPaths.addAll(favoritesAndNotifications.keySet());
    assertThat(fields(spec.path("paths"))).isEqualTo(expectedPaths);
    assertThat(spec.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
    assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    publicPaths.forEach((path, method) -> {
      var documented = operation(path, method);
      assertThat(documented.path("security").isEmpty()).as(path).isTrue();
      assertThat(fields(spec.path("paths").path(path))).containsExactly(method);
    });
    protectedPaths.forEach((path, method) -> {
      var documented = operation(path, method);
      assertThat(documented.at("/security/0/bearerAuth").isArray()).as(path).isTrue();
      if (path.contains("{")) {
        assertThat(documented.at("/parameters/0/in").asText()).as(path).isEqualTo("path");
        assertThat(documented.at("/parameters/0/required").asBoolean()).as(path).isTrue();
      } else if (!path.equals("/search/analyses/recent")) {
        assertThat(documented.path("parameters").isEmpty()).as(path).isTrue();
      }
      assertThat(fields(spec.path("paths").path(path))).containsExactly(method);
    });
    optionalBearer.forEach((path, method) -> {
      var documented = operation(path, method);
      assertThat(documented.at("/security/0").isObject()).as(path).isTrue();
      assertThat(documented.at("/security/0").isEmpty()).as(path).isTrue();
      assertThat(documented.at("/security/1/bearerAuth").isArray()).as(path).isTrue();
      assertThat(fields(spec.path("paths").path(path))).containsExactly(method);
    });
    favoritesAndNotifications.forEach((path, methods) -> {
      assertThat(fields(spec.path("paths").path(path))).isEqualTo(methods);
      methods.forEach(method -> assertThat(operation(path, method).at("/security/0/bearerAuth").isArray())
          .as("%s %s", method, path).isTrue());
    });
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
  }

  @Test
  void documentsRequiredBodiesPartialPatchAndQueryParameters() {
    checkBody("/auth/signup", "post", "loginId", "password", "nickname", "email", "termsAgreed", "privacyAgreed");
    checkBody("/auth/login", "post", "loginId", "password");
    checkBody("/auth/reissue", "post", "refreshToken");
    checkBody("/auth/logout", "post", "refreshToken");
    checkBody("/my/password", "patch", "currentPassword", "newPassword");
    checkBody("/my/account", "delete", "password");
    checkBody("/search/analyses/manual", "post", "productName", "wholesalePrice", "shippingFee",
        "hasPricePolicy");
    checkBody("/search/products/resolve-url", "post", "url");
    var urlAnalysis = checkBody("/search/analyses/url", "post", "url");
    assertThat(fields(urlAnalysis.path("properties"))).containsExactlyInAnyOrder("url", "keyword", "selectedOptionId");
    var feedback = checkBody("/search/reports/{reportId}/feedback", "post", "feedback");
    var reportPatch = checkBody("/search/reports/{reportId}/inputs", "patch");
    assertThat(fields(reportPatch.path("properties"))).containsExactlyInAnyOrder(
        "expectedSellingPrice", "coupangCategoryCode", "keyword", "selectedOptionId", "cpc");
    assertThat(reportPatch.path("anyOf")).extracting(schema -> schema.at("/required/0").asText())
        .containsExactlyInAnyOrder("expectedSellingPrice", "coupangCategoryCode", "keyword", "selectedOptionId", "cpc");
    assertThat(reportPatch.at("/properties/cpc/type").toString()).contains("null");
    assertThat(resolve(feedback.at("/properties/feedback")).path("enum"))
        .extracting(JsonNode::asText).containsExactlyInAnyOrder("APPROPRIATE", "TOO_HIGH", "TOO_LOW");
    var marketing = checkBody("/my/marketing-consent", "patch", "agreed");
    assertThat(marketing.at("/properties/agreed/type").asText()).isEqualTo("boolean");
    var profile = checkBody("/my/profile", "patch");
    assertThat(fields(profile.path("properties"))).containsExactlyInAnyOrder("nickname", "email");
    assertThat(profile.at("/anyOf/0/required/0").asText()).isEqualTo("nickname");
    assertThat(profile.at("/anyOf/1/required/0").asText()).isEqualTo("email");
    var query = operation("/auth/check-login-id", "get").at("/parameters/0");
    assertThat(query.path("name").asText()).isEqualTo("loginId");
    assertThat(query.path("in").asText()).isEqualTo("query");
    assertThat(query.path("required").asBoolean()).isTrue();
    var recent = operation("/search/analyses/recent", "get").path("parameters");
    assertThat(recent).extracting(parameter -> parameter.path("name").asText()).containsExactly("page", "size");
    assertThat(recent.at("/0/schema/default").asInt()).isZero();
    assertThat(recent.at("/1/schema/default").asInt()).isEqualTo(10);
    assertThat(recent.at("/1/schema/maximum").asInt()).isEqualTo(100);
    checkBody("/favorites", "post", "reportId", "status");
    checkBody("/search/screenings", "post", "keywords");
    checkBody("/search/screenings/{screeningId}/shopping-analysis", "post", "keyword", "categoryCode");
    checkBody("/favorites/{favoriteId}/status", "patch", "status");
    checkBody("/favorites/{favoriteId}/sales-results", "post", "result");
    var settings = checkBody("/my/notification-settings", "patch");
    assertThat(fields(settings.path("properties"))).containsExactlyInAnyOrder("emailEnabled", "eventTypes", "thresholds");
  }

  @Test
  void documentsExactStatusSetsAndErrorEnvelopes() {
    statuses("/auth/signup", "post", "201", "400", "401", "403", "409", "415", "500");
    statuses("/auth/login", "post", "200", "400", "401", "415", "500");
    statuses("/auth/reissue", "post", "200", "400", "401", "415", "500");
    statuses("/auth/logout", "post", "200", "400", "401", "403", "415", "500");
    statuses("/auth/check-login-id", "get", "200", "400", "401", "500");
    statuses("/my/dashboard", "get", "200", "401", "500");
    statuses("/home/report-usage", "get", "200", "401", "500");
    statuses("/my/profile", "patch", "200", "400", "401", "409", "415", "500");
    statuses("/my/password", "patch", "200", "400", "401", "415", "500");
    statuses("/my/account", "delete", "200", "400", "401", "415", "500");
    statuses("/my/marketing-consent", "patch", "200", "400", "401", "415", "500");
    statuses("/search/analyses/manual", "post", "202", "400", "401", "409", "415", "429", "500", "503");
    statuses("/search/products/resolve-url", "post", "200", "400", "401", "415", "500", "503");
    statuses("/search/coupang-categories", "get", "200", "401", "500");
    statuses("/search/analyses/url", "post", "202", "400", "401", "409", "415", "429", "500", "503");
    statuses("/search/analyses/{analysisId}/status", "get", "200", "400", "401", "404", "500");
    statuses("/search/reports/{reportId}", "get", "200", "400", "401", "404", "500");
    statuses("/search/analyses/recent", "get", "200", "400", "401", "500");
    statuses("/search/reports/{reportId}/feedback", "post", "201", "400", "401", "404", "409", "415", "500");
    statuses("/search/reports/{reportId}/inputs", "patch", "200", "400", "401", "404", "409", "415", "500", "503");
    statuses("/favorites", "post", "200", "201", "400", "401", "404", "409", "415", "500");
    statuses("/home/notifications/{notificationId}/read", "patch", "200", "400", "401", "404", "500");
    statuses("/search/screenings", "post", "200", "400", "401", "415", "500", "503");
    statuses("/search/screenings/{screeningId}/shopping-analysis", "post", "200", "400", "401", "404", "415", "500", "503");
    var unauthorized = operation("/auth/reissue", "post").at("/responses/401/content/application~1json");
    assertThat(fields(unauthorized.path("examples"))).containsExactlyInAnyOrder("A003", "A004", "A005");
    var error = unauthorized.at("/examples/A005/value");
    assertThat(fields(error)).containsExactlyInAnyOrder("status", "message", "errors", "code");
    assertThat(error.path("status").intValue()).isEqualTo(401);
    assertThat(error.path("code").asText()).isEqualTo("A005");
    assertThat(error.path("errors").isArray()).isTrue();
    assertThat(error.path("errors").isEmpty()).isTrue();
    assertThat(fields(resolve(unauthorized.path("schema")).path("properties")))
        .containsExactlyInAnyOrder("message", "status", "errors", "code");
  }

  @Test
  void documentsResponseEnvelopesAndNullFields() {
    var favorite = successData("/favorites", "post", "201");
    var shopping = successData("/search/screenings/{screeningId}/shopping-analysis", "post", "200");
    assertThat(shopping.at("/properties/quadrant/anyOf/1/type").toString()).contains("null");
    assertThat(favorite.at("/properties/favoriteId/type").asText()).isEqualTo("integer");
    assertThat(successData("/favorites", "post", "200")).isEqualTo(favorite);
    assertThat(successData("/favorites/{favoriteId}", "delete", "200").path("type").toString()).contains("null");
    var signup = successData("/auth/signup", "post", "201");
    assertThat(fields(signup.path("properties")))
        .containsExactlyInAnyOrder("userId", "loginId", "nickname", "email", "createdAt");
    assertThat(fields(successData("/auth/login", "post", "200").path("properties")))
        .containsExactlyInAnyOrder("userId", "nickname", "accessToken", "refreshToken", "tokenType", "expiresIn");
    assertThat(fields(successData("/auth/reissue", "post", "200").path("properties")))
        .containsExactlyInAnyOrder("accessToken", "refreshToken", "tokenType", "expiresIn");
    assertThat(fields(successData("/auth/check-login-id", "get", "200").path("properties")))
        .containsExactlyInAnyOrder("loginId", "available");
    assertThat(fields(successData("/my/profile", "patch", "200").path("properties")))
        .containsExactlyInAnyOrder("userId", "nickname", "email", "updatedAt");
    assertThat(fields(successData("/home/report-usage", "get", "200").path("properties")))
        .containsExactlyInAnyOrder("isLoggedIn", "plan", "period", "periodType", "usedCount", "limitCount",
            "remainingCount");
    var dashboard = successData("/my/dashboard", "get", "200").path("properties");
    assertThat(fields(dashboard)).containsExactlyInAnyOrder("profile", "plan", "reportUsage", "screeningUsage",
        "favoriteUsage", "marketingConsent");
    assertThat(resolve(dashboard.path("reportUsage")).at("/properties/nextResetAt/format").asText())
        .isEqualTo("date-time");
    var consent = successData("/my/marketing-consent", "patch", "200").path("properties");
    assertThat(fields(consent)).containsExactlyInAnyOrder("agreed", "updatedAt");
    assertThat(consent.at("/updatedAt/type").toString()).contains("string", "null");
    assertThat(successData("/auth/logout", "post", "200").path("type").toString()).contains("null");
    assertThat(successData("/my/password", "patch", "200").path("type").toString()).contains("null");
    assertThat(successData("/my/account", "delete", "200").path("type").toString()).contains("null");
    assertThat(operation("/auth/logout", "post").at("/responses/200/content/application~1json/example/data").isNull())
        .isTrue();
  }

  @Test
  void documentsTypedReportAndRecentAnalysisPayloads() {
    var detail = successData("/search/reports/{reportId}", "get", "200").path("properties");
    assertThat(fields(detail)).containsExactlyInAnyOrder("reportId", "analysisId", "analysisType", "source",
        "analyzedAt", "product", "summary", "internalDiagnosis", "externalDiagnosis", "investmentStrategy",
        "investmentSimulation", "unavailableFeatures", "notices");
    var product = resolve(detail.path("product"));
    assertThat(product.at("/properties/purchaseUrl/type").toString()).contains("string", "null");
    assertThat(product.at("/properties/supplyConditions/$ref").isMissingNode()).isTrue();
    assertThat(product.at("/properties/supplyConditions/anyOf/1/type").toString()).contains("null");
    assertThat(resolve(product.at("/properties/supplyConditions/anyOf/0")).path("properties").has("returnAllowed"))
        .isTrue();
    var registration = resolve(resolve(detail.path("investmentStrategy")).at("/properties/coupangRegistration"));
    assertThat(registration.at("/properties/requiredCertifications/type").toString()).contains("array");

    var recent = successData("/search/analyses/recent", "get", "200");
    var item = resolve(recent.at("/properties/analyses/items"));
    assertThat(item.at("/properties/analyzedAt/type").toString()).contains("string", "null");
    assertThat(item.at("/properties/reportId/type").toString()).contains("integer", "null");
    assertThat(item.at("/properties/investmentGrade/anyOf/1/type").toString()).contains("null");
    assertThat(resolve(item.at("/properties/investmentGrade/anyOf/0")).path("enum"))
        .extracting(JsonNode::asText).containsExactlyInAnyOrder("SUITABLE", "CONDITIONAL", "NOT_RECOMMENDED");
  }

  private JsonNode operation(String path, String method) {
    return spec.path("paths").path(path).path(method);
  }

  private JsonNode checkBody(String path, String method, String... required) {
    var body = operation(path, method).path("requestBody");
    assertThat(body.path("required").asBoolean()).as(path).isTrue();
    var schema = resolve(body.at("/content/application~1json/schema"));
    assertThat(schema.path("required")).extracting(JsonNode::asText).containsExactlyInAnyOrder(required);
    return schema;
  }

  private void statuses(String path, String method, String... expected) {
    assertThat(fields(operation(path, method).path("responses"))).as(path).containsExactlyInAnyOrder(expected);
  }

  private JsonNode successData(String path, String method, String status) {
    var schema = resolve(operation(path, method).at("/responses/" + status + "/content/application~1json/schema"));
    assertThat(fields(schema.path("properties"))).as(path).containsExactlyInAnyOrder("status", "message", "data");
    return resolve(schema.at("/properties/data"));
  }

  private JsonNode resolve(JsonNode schema) {
    String reference = schema.path("$ref").asText();
    return reference.isEmpty() ? schema : spec.at(reference.substring(1));
  }

  private static Set<String> fields(JsonNode node) {
    var fields = new HashSet<String>();
    node.fieldNames().forEachRemaining(fields::add);
    return fields;
  }
}
