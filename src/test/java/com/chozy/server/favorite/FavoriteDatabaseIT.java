package com.chozy.server.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ReportDataFixture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class FavoriteDatabaseIT extends AccountPostgresITSupport {
  private static final String FAVORITES = "/favorites";
  private static final String SNAPSHOT = """
      {
        "externalDiagnosis":{"keyword":"changed keyword"},
        "summary":{"score":75,"supplyConditionScore":78,"marketConditionScore":73},
        "internalDiagnosis":{"profitability":{"expectedSellingPrice":15800,"profitPerUnit":2923,"marginRate":18.5}}
      }
      """;

  @Test
  void managesOwnedManualFavoriteAndKeepsRegistrationAndSalesHistory() throws Exception {
    var owner = account();
    var other = account();
    long report = report(owner, "Lantern", SNAPSHOT);
    authenticate(register(report, "INTERESTED"), other).andExpect(status().isNotFound());
    long id = favoriteId(owner, report);
    String path = FAVORITES + "/" + id;
    var createdAt = jdbc.sql("SELECT created_at FROM chozy.member_favorites WHERE id = :id")
        .param("id", id).query(java.time.OffsetDateTime.class).single();

    jdbc.sql("UPDATE chozy.manual_analysis_reports SET snapshot = '{}'::jsonb WHERE id = :id")
        .param("id", report).update();
    authenticate(register(report, "SELLING"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data.favoriteId").value(id))
        .andExpect(jsonPath("$.data.savedScore").value(75));

    authenticate(get(FAVORITES), owner).andExpect(jsonPath("$.data.items").isEmpty())
        .andExpect(jsonPath("$.data.counts.selling").value(1))
        .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    authenticate(get(FAVORITES).param("status", "SELLING"), owner)
        .andExpect(jsonPath("$.data.items[0].favoriteId").value(id))
        .andExpect(jsonPath("$.data.items[0].scoreChange").value(0));
    authenticate(get(path), other).andExpect(status().isNotFound());
    authenticate(patch(path + "/status").contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"INTERESTED\"}"), owner)
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.previousStatus").value("SELLING"))
        .andExpect(jsonPath("$.data.counts.interested").value(1));

    authenticate(post(path + "/sales-results").contentType(MediaType.APPLICATION_JSON)
        .content("{\"result\":\"GOOD\"}"), other).andExpect(status().isNotFound());
    authenticate(post(path + "/sales-results").contentType(MediaType.APPLICATION_JSON)
        .content("{\"result\":\"GOOD\"}"), owner).andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.scoreAtRecording").value(75))
        .andExpect(jsonPath("$.data.supplyConditionScoreAtRecording").value(78));
    authenticate(post(path + "/sales-results").contentType(MediaType.APPLICATION_JSON)
        .content("{\"result\":\"NOT_LISTED\"}"), owner).andExpect(status().isCreated());

    authenticate(get(path).param("periodDays", "7"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.product.expectedSellingPrice").value(15800))
        .andExpect(jsonPath("$.data.product.profitPerUnit").value(2923))
        .andExpect(jsonPath("$.data.priceHistory").hasJsonPath())
        .andExpect(jsonPath("$.data.priceHistory").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.data.scoreHistory.marketPriceRefreshStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.data.status").value("INTERESTED"))
        .andExpect(jsonPath("$.data.salesResult.result").value("NOT_LISTED"));
    authenticate(get(path).param("periodDays", "60"), owner).andExpect(status().isBadRequest());
    assertThat(jdbc.sql("SELECT keyword FROM chozy.member_favorites WHERE id = :id")
        .param("id", id).query(String.class).single()).isEqualTo("changed keyword");
    assertThat(jdbc.sql("SELECT created_at FROM chozy.member_favorites WHERE id = :id")
        .param("id", id).query(java.time.OffsetDateTime.class).single()).isEqualTo(createdAt);

    authenticate(delete(path), other).andExpect(status().isNotFound());
    authenticate(delete(path), owner).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    authenticate(get(path), owner).andExpect(status().isNotFound());
    authenticate(get("/my/dashboard"), owner).andExpect(jsonPath("$.data.favoriteUsage.usedCount").value(0))
        .andExpect(jsonPath("$.data.reportUsage.usedCount").value(0));
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.favorite_sales_results WHERE favorite_id = :id")
        .param("id", id).query(Long.class).single()).isEqualTo(2L);
    assertThat(favoriteId(owner, report)).isNotEqualTo(id);
  }

  @Test
  void enforcesLimitAcrossStatusesEvenWhenTwoRequestsCompete() throws Exception {
    var owner = account();
    long existing = report(owner, "First", "{}");
    favoriteId(owner, existing);
    for (int i = 0; i < 8; i++) {
      authenticate(register(report(owner, "Queued " + i, "{}"), "SELLING"), owner).andExpect(status().isCreated());
    }
    long first = report(owner, "Concurrent A", SNAPSHOT);
    long second = report(owner, "Concurrent B", SNAPSHOT);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(() -> {
        start.await();
        return authenticate(register(first, "INTERESTED"), owner).andReturn().getResponse().getStatus();
      });
      var b = executor.submit(() -> {
        start.await();
        return authenticate(register(second, "SELLING"), owner).andReturn().getResponse().getStatus();
      });
      start.countDown();
      assertThat(new Integer[]{a.get(), b.get()}).containsExactlyInAnyOrder(201, 409);
    }
    authenticate(register(existing, "SELLING"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.favoriteCount").value(10));
    authenticate(register(report(owner, "Over limit", "{}"), "SELLING"), owner).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("F001"));
    authenticate(get("/my/dashboard"), owner).andExpect(jsonPath("$.data.favoriteUsage.usedCount").value(10));
  }

  @Test
  void sortsCalculatedScoresBeforeMissingScoresAndPaginatesFromZero() throws Exception {
    var owner = account();
    long first = favoriteId(owner, report(owner, "Scored", SNAPSHOT));
    favoriteId(owner, report(owner, "Pending", "{}"));
    authenticate(get(FAVORITES).param("sort", "SCORE").param("size", "1"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].favoriteId").value(first))
        .andExpect(jsonPath("$.data.pagination.hasNext").value(true));
    authenticate(get(FAVORITES).param("sort", "CHANGE").param("size", "1").param("page", "1"), owner)
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].score").isEmpty())
        .andExpect(jsonPath("$.data.pagination.totalPages").value(2))
        .andExpect(jsonPath("$.data.pagination.hasNext").value(false));
  }

  private long report(Account owner, String name, String snapshot) {
    var fixture = new ReportDataFixture(jdbc);
    return fixture.report(fixture.analysis(owner.id(), name, "COMPLETED", "2026-11-23T01:01:00Z"), snapshot);
  }

  private long favoriteId(Account owner, long report) throws Exception {
    return response(authenticate(register(report, "INTERESTED"), owner).andExpect(status().isCreated()))
        .at("/data/favoriteId").asLong();
  }

  private static MockHttpServletRequestBuilder register(long report, String status) {
    return post(FAVORITES).contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"reportId":%d,"status":"%s"}
            """.formatted(report, status));
  }

  private ResultActions authenticate(MockHttpServletRequestBuilder request, Account account) throws Exception {
    return mvc.perform(request.header("Authorization", account.bearer()));
  }
}
