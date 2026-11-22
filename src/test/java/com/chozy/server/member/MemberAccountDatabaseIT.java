package com.chozy.server.member;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.support.AccountPostgresITSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MemberAccountDatabaseIT extends AccountPostgresITSupport {
  @Test
  void allFiveEndpointsRequireJwt() throws Exception {
    for (var request : List.of(get("/my/dashboard"), get("/home/report-usage"), patch("/my/profile"),
        patch("/my/password"), patch("/my/marketing-consent"))) {
      mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    }
  }

  @Test
  void migratedV2MemberCanLoginAndHasDefaultConsentAndUsage() throws Exception {
    var tokens = auth.login(new Login(V2_LOGIN, PASSWORD));
    mvc.perform(get("/my/dashboard").header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.profile.nickname").value("legacy seller"))
        .andExpect(jsonPath("$.data.plan.code").value("FREE"))
        .andExpect(jsonPath("$.data.reportUsage.usedCount").value(0))
        .andExpect(jsonPath("$.data.screeningUsage.usedCount").value(0))
        .andExpect(jsonPath("$.data.favoriteUsage.usedCount").value(0))
        .andExpect(jsonPath("$.data.marketingConsent.agreed").value(false))
        .andExpect(jsonPath("$.data.marketingConsent.updatedAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void patchUsesJwtOwnerAndOnlyAppliesSupportedFields() throws Exception {
    var first = account();
    var second = account();
    var profile = response(patchAs(first, "/my/profile", Map.of("nickname", "  전문셀러  ",
        "userId", second.id(), "loginId", "different", "password", NEW_PASSWORD))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("회원 정보가 수정되었습니다."))
        .andExpect(jsonPath("$.data.userId").value(first.id()))
        .andExpect(jsonPath("$.data.nickname").value("전문셀러"))
        .andExpect(jsonPath("$.data.email").value(first.email())));
    assertThat(OffsetDateTime.parse(profile.at("/data/updatedAt").asText()).getOffset().toString()).isEqualTo("+09:00");
    patchAs(first, "/my/profile", Map.of("email", "  NEW-ADDRESS@EXAMPLE.COM  "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("new-address@example.com"))
        .andExpect(jsonPath("$.data.nickname").value("전문셀러"));
    patchAs(first, "/my/profile", Map.of("nickname", "conflict", "email", second.email().toUpperCase(java.util.Locale.ROOT)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("C002"));
    dashboard(first).andExpect(jsonPath("$.data.profile.loginId").value(first.loginId()))
        .andExpect(jsonPath("$.data.profile.nickname").value("전문셀러"));
    dashboard(second).andExpect(jsonPath("$.data.profile.nickname").value("seller"))
        .andExpect(jsonPath("$.data.profile.email").value(second.email()));
    assertThat(auth.login(new Login(first.loginId(), PASSWORD)).userId()).isEqualTo(first.id());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
    "{}|C001",
    "{\"userId\":2}|C001",
    "{\"nickname\":\" \"}|C001",
    "{\"email\":\"invalid\"}|C001",
    "{\"nickname\":null,\"email\":\"new@example.com\"}|C009",
    "{\"nickname\":123}|C009"
  })
  void rejectsInvalidProfile(String body, String code) throws Exception {
    var member = account();
    patchJson(member, "/my/profile", body).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(code));
    dashboard(member).andExpect(jsonPath("$.data.profile.nickname").value("seller"))
        .andExpect(jsonPath("$.data.profile.email").value(member.email()));
  }

  @Test
  void consentStoresOnlyChangesAndDoesNotAffectAnotherMember() throws Exception {
    var member = account();
    var other = account();
    clock.set("2026-11-16T06:50:00Z");
    patchAs(member, "/my/marketing-consent", Map.of("agreed", false)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(org.hamcrest.Matchers.nullValue()));
    var agreed = response(patchAs(member, "/my/marketing-consent", Map.of("agreed", true, "userId", other.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("광고성 정보 수신 동의 설정이 변경되었습니다."))
        .andExpect(jsonPath("$.data.agreed").value(true))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-11-16T15:50:00+09:00")));
    clock.set("2026-11-17T06:50:00Z");
    assertThat(response(patchAs(member, "/my/marketing-consent", Map.of("agreed", true)))
        .get("data")).isEqualTo(agreed.get("data"));
    dashboard(other).andExpect(jsonPath("$.data.marketingConsent.agreed").value(false));
    patchAs(member, "/my/marketing-consent", Map.of("agreed", false)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.agreed").value(false))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-11-17T15:50:00+09:00"));
    dashboard(member).andExpect(jsonPath("$.data.marketingConsent.agreed").value(false))
        .andExpect(jsonPath("$.data.marketingConsent.updatedAt").value("2026-11-17T15:50:00+09:00"));
    var history = jdbc.sql("""
        SELECT agreed, changed_at FROM chozy.marketing_consent_history WHERE member_id = :id ORDER BY id
        """).param("id", member.id()).query((row, index) -> Map.entry(row.getBoolean("agreed"),
            row.getObject("changed_at", OffsetDateTime.class).toInstant().toString())).list();
    assertThat(history).containsExactly(Map.entry(true, "2026-11-16T06:50:00Z"),
        Map.entry(false, "2026-11-17T06:50:00Z"));
  }

  @Test
  void rejectsMissingOrNonBooleanConsent() throws Exception {
    var member = account();
    patchJson(member, "/my/marketing-consent", "{}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C001"));
    patchJson(member, "/my/marketing-consent", "{\"agreed\":\"true\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C009"));
    assertThat(jdbc.sql("select count(*) from chozy.marketing_consent_history where member_id = :id")
        .param("id", member.id()).query(Long.class).single()).isZero();
  }

  private ResultActions dashboard(Account member) throws Exception {
    return mvc.perform(get("/my/dashboard").header("Authorization", member.bearer()))
        .andExpect(status().isOk());
  }

  private ResultActions patchAs(Account member, String path, Map<String, ?> body) throws Exception {
    return patchJson(member, path, mapper.writeValueAsString(body));
  }

  private ResultActions patchJson(Account member, String path, String body) throws Exception {
    return mvc.perform(patch(path).header("Authorization", member.bearer())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
