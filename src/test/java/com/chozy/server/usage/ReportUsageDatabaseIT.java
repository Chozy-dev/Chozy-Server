package com.chozy.server.usage;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.usage.service.ReportQuotaExceededException;
import com.chozy.server.usage.service.ReportUsageService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

class ReportUsageDatabaseIT extends AccountPostgresITSupport {
  private static final AtomicLong REPORT_IDS = new AtomicLong();

  @Autowired ReportUsageService usage;

  @Test
  void bothApisReadSameLedgerAndDashboardUsesOnlyOwnersStoredActivity() throws Exception {
    var member = account();
    var other = account();
    clock.set("2026-11-16T10:00:00Z");
    var joined = OffsetDateTime.parse("2026-11-15T14:59:59Z");
    jdbc.sql("UPDATE chozy.members SET created_at = :created WHERE id = :id")
        .param("created", joined).param("id", member.id()).update();
    screening(member.id(), "2026-10-31T14:59:59Z");
    screening(member.id(), "2026-10-31T15:00:00Z");
    screening(member.id(), "2026-11-10T18:30:00Z");
    screening(member.id(), "2026-11-30T15:00:00Z");
    screening(other.id(), "2026-11-10T18:30:00Z");
    favorite(member.id(), "INTERESTED");
    favorite(member.id(), "SELLING");
    favorite(other.id(), "SELLING");
    record(member.id(), REPORT_IDS.incrementAndGet(), false);

    var home = response(mvc.perform(get("/home/report-usage").header("Authorization", member.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("정밀분석 사용량 조회가 완료되었습니다."))
        .andExpect(jsonPath("$.data.isLoggedIn").value(true)).andExpect(jsonPath("$.data.plan").value("FREE"))
        .andExpect(jsonPath("$.data.nextResetAt").doesNotExist())).get("data");
    var dashboard = response(mvc.perform(get("/my/dashboard").header("Authorization", member.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("마이페이지 정보 조회가 완료되었습니다."))
        .andExpect(jsonPath("$.data.profile.membershipDays").value(2))
        .andExpect(jsonPath("$.data.plan.name").value("무료 플랜"))
        .andExpect(jsonPath("$.data.reportUsage.nextResetAt").value("2026-12-01T00:00:00+09:00"))
        .andExpect(jsonPath("$.data.screeningUsage.period").value("2026-11"))
        .andExpect(jsonPath("$.data.screeningUsage.usedCount").value(2))
        .andExpect(jsonPath("$.data.screeningUsage.unlimited").value(true))
        .andExpect(jsonPath("$.data.favoriteUsage.usedCount").value(2))
        .andExpect(jsonPath("$.data.favoriteUsage.limitCount").value(10))).get("data");
    for (String field : java.util.List.of("period", "periodType", "usedCount", "limitCount", "remainingCount")) {
      assertThat(home.get(field)).isEqualTo(dashboard.get("reportUsage").get(field));
    }
    assertThat(home.get("usedCount").asInt()).isEqualTo(1);
    mvc.perform(get("/my/dashboard").param("userId", Long.toString(member.id())).header("Authorization", other.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.profile.userId").value(other.id()))
        .andExpect(jsonPath("$.data.reportUsage.usedCount").value(0))
        .andExpect(jsonPath("$.data.screeningUsage.usedCount").value(1))
        .andExpect(jsonPath("$.data.favoriteUsage.usedCount").value(1));
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void kstMonthRollsOverAtMidnightAndRetriesStayInTheOriginalMonth() {
    var member = account();
    long report = REPORT_IDS.incrementAndGet();
    clock.set("2026-12-31T14:59:59.999999999Z");
    record(member.id(), report, false);
    assertThat(usage.current(member.id()).period()).isEqualTo("2026-12");
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(1);
    assertThat(usage.current(member.id()).nextResetAt()).isEqualTo(OffsetDateTime.parse("2027-01-01T00:00:00+09:00"));
    clock.set("2026-12-31T15:00:00Z");
    record(member.id(), report, false);
    assertThat(usage.current(member.id()).period()).isEqualTo("2027-01");
    assertThat(usage.current(member.id()).usedCount()).isZero();
    assertThat(usage.current(member.id()).remainingCount()).isEqualTo(3);
    record(member.id(), REPORT_IDS.incrementAndGet(), false);
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void chargeRollsBackWithCallerAndCannotRunOutsideItsTransaction() {
    var member = account();
    long report = REPORT_IDS.incrementAndGet();
    assertThatThrownBy(() -> usage.recordCreatedReport(member.id(), report, false))
        .isInstanceOf(IllegalTransactionStateException.class);
    transactions.executeWithoutResult(status -> {
      usage.recordCreatedReport(member.id(), report, false);
      status.setRollbackOnly();
    });
    assertThat(usage.current(member.id()).usedCount()).isZero();
    record(member.id(), report, false);
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void atLimitExemptReportSucceedsAndRetryDoesNotCharge() {
    var member = account();
    long exempt = REPORT_IDS.incrementAndGet();
    for (int i = 0; i < 3; i++) record(member.id(), REPORT_IDS.incrementAndGet(), false);
    assertThatThrownBy(() -> record(member.id(), REPORT_IDS.incrementAndGet(), false))
        .isInstanceOf(ReportQuotaExceededException.class);
    record(member.id(), exempt, true);
    record(member.id(), exempt, false);
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(3);
    assertThat(usage.current(member.id()).remainingCount()).isZero();
    assertThat(jdbc.sql("SELECT data_lab_quota_exhausted FROM chozy.report_usage_entries WHERE report_id = :id")
        .param("id", exempt).query(Boolean.class).single()).isTrue();
  }

  @Test
  void concurrentDistinctReportsCannotExceedLimit() throws Exception {
    var member = account();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 8; i++) {
        long report = REPORT_IDS.incrementAndGet();
        results.add(executor.submit(() -> {
          start.await();
          try {
            record(member.id(), report, false);
            return true;
          } catch (ReportQuotaExceededException ex) {
            return false;
          }
        }));
      }
      start.countDown();
      int accepted = 0;
      for (var result : results) {
        if (result.get(15, TimeUnit.SECONDS)) accepted++;
      }
      assertThat(accepted).isEqualTo(3);
      assertThat(usage.current(member.id()).usedCount()).isEqualTo(3);
    }
  }

  @Test
  void concurrentRetriesOfOneReportChargeOnceAndAnotherMemberCannotClaimItsId() throws Exception {
    var member = account();
    var other = account();
    long report = REPORT_IDS.incrementAndGet();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var retries = new ArrayList<Future<?>>();
      for (int i = 0; i < 5; i++) {
        retries.add(executor.submit(() -> {
          start.await();
          record(member.id(), report, false);
          return null;
        }));
      }
      start.countDown();
      for (var retry : retries) retry.get(15, TimeUnit.SECONDS);
    }
    assertThat(usage.current(member.id()).usedCount()).isEqualTo(1);
    assertThatThrownBy(() -> record(other.id(), report, false)).isInstanceOf(IllegalArgumentException.class);
    assertThat(usage.current(other.id()).usedCount()).isZero();
  }

  private void record(long memberId, long reportId, boolean dataLabQuotaExhausted) {
    transactions.executeWithoutResult(status -> usage.recordCreatedReport(memberId, reportId, dataLabQuotaExhausted));
  }

  private void screening(long memberId, String executedAt) {
    jdbc.sql("INSERT INTO chozy.member_screening_runs (member_id, executed_at) VALUES (:id, :executed)")
        .param("id", memberId).param("executed", OffsetDateTime.parse(executedAt)).update();
  }

  private void favorite(long memberId, String status) {
    jdbc.sql("INSERT INTO chozy.member_favorites (member_id, status) VALUES (:id, :status)")
        .param("id", memberId).param("status", status).update();
  }
}
