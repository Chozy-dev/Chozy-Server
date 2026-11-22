package com.chozy.server.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ReportDataFixture;
import com.chozy.server.usage.service.ReportUsageService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReportFeedbackDatabaseIT extends AccountPostgresITSupport {
  private static final String FEEDBACK = "/search/reports/{id}/feedback";
  private static final String VALID = """
      {"feedback":"TOO_HIGH","score":99}
      """;
  private static final String SCORED = """
      {"summary":{"scoreStatus":"CALCULATED","score":72.5,"marketConditionScore":73}}
      """;

  @Autowired ReportUsageService usage;

  @Test
  void snapshotsTheStoredScoreWithoutChangingFreeUsage() throws Exception {
    clock.set("2026-11-23T01:01:00Z");
    var owner = account();
    long report = report(owner, SCORED);
    transactions.executeWithoutResult(tx -> usage.recordCreatedReport(owner.id(), report, false));
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
    mvc.perform(get("/search/reports/{id}", report).header("Authorization", owner.bearer()))
        .andExpect(status().isOk());
    mvc.perform(get("/search/analyses/recent").header("Authorization", owner.bearer()))
        .andExpect(status().isOk());

    submit(owner, report, VALID).andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.reportId").value(report))
        .andExpect(jsonPath("$.data.productId").value(nullValue()))
        .andExpect(jsonPath("$.data.score").value(72.5))
        .andExpect(jsonPath("$.data.supplyConditionScore").value(nullValue()))
        .andExpect(jsonPath("$.data.marketConditionScore").value(73))
        .andExpect(jsonPath("$.data.feedback").value("TOO_HIGH"))
        .andExpect(jsonPath("$.data.createdAt").value("2026-11-23T10:01:00+09:00"));
    jdbc.sql("""
        UPDATE chozy.manual_analysis_reports
        SET snapshot = jsonb_set(snapshot, '{summary,score}', '82'::jsonb)
        WHERE id = :report
        """).param("report", report).update();
    assertThat(jdbc.sql("SELECT score FROM chozy.report_feedback WHERE report_id = :id")
        .param("id", report).query(java.math.BigDecimal.class).single()).isEqualByComparingTo("72.5");
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void rejectsUnknownOwnersUncalculatedScoresAndMalformedFeedback() throws Exception {
    var owner = account();
    var other = account();
    long report = report(owner, """
        {"summary":{"scoreStatus":"WAITING_FOR_INPUT","requiredInputs":["COUPANG_CATEGORY"]}}
        """);
    mvc.perform(post(FEEDBACK, report).contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isUnauthorized());
    submit(other, report, VALID).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C003"));
    submit(owner, Long.MAX_VALUE, VALID).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C003"));
    submit(owner, report, VALID).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("R004"));
    submit(owner, report, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    submit(owner, report, "{\"feedback\":0}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C009"));
    submit(owner, report, "{\"feedback\":\"UNSUPPORTED\"}").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C009"));
    assertThat(usage.current(owner.id()).usedCount()).isZero();
  }

  @Test
  void concurrentSubmissionsStoreOneOpinionPerReport() throws Exception {
    var owner = account();
    long report = report(owner, SCORED);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = pool.submit(() -> {
        start.await();
        return submit(owner, report, VALID).andReturn().getResponse().getStatus();
      });
      var second = pool.submit(() -> {
        start.await();
        return submit(owner, report, VALID).andReturn().getResponse().getStatus();
      });
      start.countDown();
      assertThat(new int[] {first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
          .containsExactlyInAnyOrder(201, 409);
    }
    submit(owner, report, VALID).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("C002"));
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.report_feedback WHERE report_id = :id")
        .param("id", report).query(Long.class).single()).isEqualTo(1);
  }

  private long report(Account owner, String snapshot) {
    var data = new ReportDataFixture(jdbc);
    long analysis = data.analysis(owner.id(), "Feedback lantern", "COMPLETED", "2026-11-23T01:00:59Z");
    return data.report(analysis, snapshot);
  }

  private ResultActions submit(Account owner, long report, String input) throws Exception {
    return mvc.perform(post(FEEDBACK, report).header("Authorization", owner.bearer())
        .contentType(MediaType.APPLICATION_JSON).content(input));
  }
}
