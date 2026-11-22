package com.chozy.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
  "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "app.security.docs-public=true"
})
class OpenApiContractDatabaseIT extends AccountPostgresITSupport {
  private JsonNode spec;

  @BeforeEach
  void loadOpenApi() throws Exception {
    spec = response(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
  }

  @Test
  void exposesOnlyImplementedOperationsWithTheCorrectBearerRequirements() throws Exception {
    var publicPaths = Map.of("/auth/signup", "post", "/auth/login", "post",
        "/auth/check-login-id", "get", "/auth/reissue", "post");
    var protectedPaths = Map.of("/auth/logout", "post", "/my/dashboard", "get",
        "/my/profile", "patch", "/my/password", "patch", "/my/marketing-consent", "patch",
        "/home/report-usage", "get");
    var expectedPaths = new HashSet<>(publicPaths.keySet());
    expectedPaths.addAll(protectedPaths.keySet());
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
      assertThat(documented.path("parameters").isEmpty()).as(path).isTrue();
      assertThat(fields(spec.path("paths").path(path))).containsExactly(method);
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
    statuses("/my/marketing-consent", "patch", "200", "400", "401", "415", "500");
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
    assertThat(operation("/auth/logout", "post").at("/responses/200/content/application~1json/example/data").isNull())
        .isTrue();
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
