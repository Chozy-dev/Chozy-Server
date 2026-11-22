package com.chozy.server.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.analysis.service.ManualAnalysisEngine;
import com.chozy.server.analysis.service.ManualAnalysisService;
import com.chozy.server.analysis.service.ManualAnalysisWorker;
import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ManualAnalysisFixture;
import com.chozy.server.usage.service.ReportUsageService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

@Import(ManualAnalysisFixture.class)
@TestPropertySource(properties = {"app.analysis.worker-cron=-", "app.usage.free-monthly-report-limit=1"})
class ManualAnalysisDatabaseIT extends AccountPostgresITSupport {
  private static final String SUBMIT = "/search/analyses/manual";
  private static final String STATUS = "/search/analyses/{id}/status";
  private static final String REPORT = "/search/reports/{id}";
  private static final String INPUT = """
      {"productName":" Lantern ","wholesalePrice":8000,"shippingFee":0,"hasPricePolicy":false}
      """;

  @Autowired ManualAnalysisWorker worker;
  @Autowired ManualAnalysisService service;
  @Autowired ReportUsageService usage;

  @Test
  void savesOwnedSnapshotAndChargesOnceOnCompletion() throws Exception {
    var owner = account();
    var other = account();
    long id = accepted(owner, INPUT);
    assertThat(usage.current(owner.id()).usedCount()).isZero();

    mvc.perform(get(STATUS, id).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("PENDING"));
    mvc.perform(get(STATUS, id).header("Authorization", other.bearer())).andExpect(status().isNotFound());

    worker.runNext();
    worker.runNext();
    long reportId = response(mvc.perform(get(STATUS, id).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.currentStep").value(7))).at("/data/reportId").longValue();
    mvc.perform(get(REPORT, reportId).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.reportId").value(reportId))
        .andExpect(jsonPath("$.data.analysisId").value(id))
        .andExpect(jsonPath("$.data.product.productName").value("Lantern"));
    mvc.perform(get(REPORT, reportId).header("Authorization", other.bearer())).andExpect(status().isNotFound());
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
    submit(owner, INPUT).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("R001"));
  }

  @Test
  void failureAndDataLabExemptionReleaseReservationWithoutCharging() throws Exception {
    var owner = account();
    long failed = accepted(owner, withKeyword("FAIL"));
    submit(owner, INPUT).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("R002"));

    worker.runNext();
    mvc.perform(get(STATUS, failed).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.reportId").isEmpty());
    long exempt = accepted(owner, withKeyword("EXEMPT"));
    worker.runNext();
    long report = response(mvc.perform(get(STATUS, exempt).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("COMPLETED")))
        .at("/data/reportId").longValue();
    assertThat(jdbc.sql("SELECT data_lab_quota_exhausted FROM chozy.report_usage_entries WHERE report_id = :id")
        .param("id", report).query(Boolean.class).single()).isTrue();
    assertThat(usage.current(owner.id()).remainingCount()).isEqualTo(1);
  }

  @Test
  void serializesLastSlotAndExpiresAnInterruptedWorker() throws Exception {
    clock.set("2026-11-23T01:00:00Z");
    var owner = account();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> {
        start.await();
        return submit(owner, INPUT).andReturn().getResponse().getStatus();
      });
      var second = executor.submit(() -> {
        start.await();
        return submit(owner, INPUT).andReturn().getResponse().getStatus();
      });
      start.countDown();
      assertThat(new int[] {first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
          .containsExactlyInAnyOrder(202, 409);
    }
    var claimed = service.claim().orElseThrow();
    mvc.perform(get(STATUS, claimed.id()).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("PROCESSING"));
    clock.set("2026-11-23T01:05:01Z");
    worker.runNext();
    service.complete(claimed, new ManualAnalysisEngine.Result(
        mapper.createObjectNode().put("stale", true), false));
    mvc.perform(get(STATUS, claimed.id()).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("FAILED"));
    assertThat(usage.current(owner.id()).usedCount()).isZero();
    accepted(owner, INPUT);
    worker.runNext();
  }

  @Test
  void rollsBackTheReportIfTheCompletionCannotBeCharged() throws Exception {
    var owner = account();
    long id = accepted(owner, INPUT);
    transactions.executeWithoutResult(tx -> usage.recordCreatedReport(owner.id(), 2_000_000, false));
    worker.runNext();
    mvc.perform(get(STATUS, id).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.reportId").isEmpty());
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.manual_analysis_reports WHERE analysis_id = :id")
        .param("id", id).query(Long.class).single()).isZero();
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void rejectsMissingAuthenticationAndInvalidPricePolicy() throws Exception {
    var owner = account();
    mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(INPUT))
        .andExpect(status().isUnauthorized());
    submit(owner, INPUT.replace("false", "true")).andExpect(status().isBadRequest());
    submit(owner, INPUT.replace("8000", "0")).andExpect(status().isBadRequest());
    submit(owner, INPUT.replace("\"shippingFee\":0,", "")).andExpect(status().isBadRequest());
  }

  private long accepted(Account owner, String input) throws Exception {
    return response(submit(owner, input).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value(202)).andExpect(jsonPath("$.data.source").value("MANUAL"))
        .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"))).at("/data/analysisId").longValue();
  }

  private ResultActions submit(Account owner, String input) throws Exception {
    return mvc.perform(post(SUBMIT).header("Authorization", owner.bearer())
        .contentType(MediaType.APPLICATION_JSON).content(input));
  }

  private String withKeyword(String keyword) {
    return INPUT.replace("false}", "false,\"keyword\":\"" + keyword + "\"}");
  }
}
