package com.chozy.server.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ScreeningDataFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(ScreeningDataFixture.class)
class ScreeningDatabaseIT extends AccountPostgresITSupport {
  private static final String SCREENINGS = "/search/screenings";
  private static final String RECENT = SCREENINGS + "/recent";

  @Test
  void guestsCanScreenAndAnalyzeTheSameKeywordUntilTheSnapshotExpires() throws Exception {
    clock.set("2026-11-23T14:55:00Z");
    var result = response(mvc.perform(screen(" lantern ", "lantern", "2", "3", "4", "5", "6", "7", "8",
            "9", "10", "11")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(10))
        .andExpect(jsonPath("$.data.excludedCount").value(1))
        .andExpect(jsonPath("$.data.keywords[0].keyword").value("lantern"))
        .andExpect(jsonPath("$.data.keywords[0].score").value(57))
        .andExpect(jsonPath("$.data.keywords[0].monthlySearchCount").value(30000))
        .andExpect(jsonPath("$.data.keywords[0].isSearchCountEstimated").value(false))
        .andExpect(jsonPath("$.data.keywords[0].clickThroughRate").value(4))
        .andExpect(jsonPath("$.data.keywords[0].mobileSearchRatio").value(66.67))
        .andExpect(jsonPath("$.data.keywords[0].searchGrowthRate").value(10))
        .andExpect(jsonPath("$.data.keywords[0].shoppingGrowthRate").value(nullValue()))
        .andExpect(jsonPath("$.data.keywords[0].searchTrend.last30Days[0].date").value("2026-10-26")));
    String id = result.at("/data/screeningId").asText();

    // The selected category uses the original date window even after midnight in Korea.
    clock.set("2026-11-23T15:05:00Z");
    mvc.perform(shopping(id, " lantern ", "50000000")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categoryName").value("패션의류"))
        .andExpect(jsonPath("$.data.score").value(58))
        .andExpect(jsonPath("$.data.shoppingGrowthRate").value(40))
        .andExpect(jsonPath("$.data.quadrant.type").value("GOLDEN_TIME"))
        .andExpect(jsonPath("$.data.shoppingTrend.last12Months[0].month").value("2026-10"))
        .andExpect(jsonPath("$.data.shoppingTrend.last30Days[27].date").value("2026-11-22"));
    mvc.perform(shopping(id, "lantern", "50005542")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.score").value(57))
        .andExpect(jsonPath("$.data.quadrant").value(nullValue()))
        .andExpect(jsonPath("$.data.shoppingTrend").value(nullValue()));
    mvc.perform(shopping(id, "11", "50000000")).andExpect(status().isNotFound());
    mvc.perform(shopping(id, "lantern", "unknown")).andExpect(status().isBadRequest());
    mvc.perform(get(RECENT)).andExpect(status().isUnauthorized());
    mvc.perform(get("/search/categories")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categories.length()").value(11));
    mvc.perform(screen("lantern").header("Authorization", "Bearer fake")).andExpect(status().isUnauthorized());

    clock.set("2026-11-24T14:55:00Z");
    mvc.perform(shopping(id, "lantern", "50000000")).andExpect(status().isNotFound());
    mvc.perform(screen("another guest")).andExpect(status().isOk());
    assertThat(jdbc.sql("""
        SELECT count(*) FROM chozy.screening_results WHERE public_id = CAST(:id AS uuid)
        """).param("id", id.substring(4)).query(Long.class).single()).isZero();
  }

  @Test
  void memberHistoryUsesTheLatestRunAndRemainsPrivateWithoutChargingAReport() throws Exception {
    clock.set("2026-11-23T10:00:00Z");
    var owner = account();
    var other = account();
    String first = response(mvc.perform(screen("old").header("Authorization", owner.bearer()))
        .andExpect(status().isOk())).at("/data/screeningId").asText();
    String latest = response(mvc.perform(screen("a", "b", "c", "d", "top", "no-trend")
            .header("Authorization", owner.bearer())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notice").isNotEmpty())
        .andExpect(jsonPath("$.data.keywords[5].trendAvailable").value(false))
        .andExpect(jsonPath("$.data.keywords[5].score").value(58))).at("/data/screeningId").asText();
    mvc.perform(get(RECENT).header("Authorization", owner.bearer())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(4))
        .andExpect(jsonPath("$.data.items[0].keyword").value("top"))
        .andExpect(jsonPath("$.data.items[0].screeningId").value(latest))
        .andExpect(jsonPath("$.data.items[1].keyword").value("no-trend"))
        .andExpect(jsonPath("$.data.items[2].keyword").value("a"));
    mvc.perform(get(RECENT).header("Authorization", other.bearer())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());
    mvc.perform(shopping(first, "old", "50000000")).andExpect(status().isNotFound());
    mvc.perform(shopping(first, "old", "50000000").header("Authorization", other.bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(shopping(first, "old", "50000000").header("Authorization", owner.bearer()))
        .andExpect(status().isOk());
    mvc.perform(screen("missing").header("Authorization", owner.bearer())).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("S001"));
    mvc.perform(get("/my/dashboard").header("Authorization", owner.bearer())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.screeningUsage.usedCount").value(2))
        .andExpect(jsonPath("$.data.reportUsage.usedCount").value(0));
  }

  @Test
  void rejectsInvalidKeywordBodiesAndCategoryCodes() throws Exception {
    mvc.perform(screen()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    mvc.perform(screen("  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    mvc.perform(post(SCREENINGS).contentType(MediaType.APPLICATION_JSON).content("{\"keywords\":[17]}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C009"));
    mvc.perform(post(SCREENINGS + "/x/shopping-analysis").contentType(MediaType.APPLICATION_JSON)
        .content("{\"keyword\":\"a\",\"categoryCode\":50000000}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C009"));
  }

  private MockHttpServletRequestBuilder screen(String... keywords) throws Exception {
    return post(SCREENINGS).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("keywords", List.of(keywords))));
  }

  private MockHttpServletRequestBuilder shopping(String id, String keyword, String code) throws Exception {
    return post(SCREENINGS + "/{screeningId}/shopping-analysis", id).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("keyword", keyword, "categoryCode", code)));
  }
}
