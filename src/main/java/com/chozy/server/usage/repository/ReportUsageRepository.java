package com.chozy.server.usage.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class ReportUsageRepository {
  private final JdbcClient jdbc;

  public ReportUsageRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long countCharged(long memberId, LocalDate period) {
    return jdbc.sql("""
        SELECT count(*) FROM chozy.report_usage_entries
        WHERE member_id = :memberId AND period_start = :period AND NOT data_lab_quota_exhausted
        """).param("memberId", memberId).param("period", period).query(Long.class).single();
  }

  public Optional<Long> findReportOwner(long reportId) {
    return jdbc.sql("SELECT member_id FROM chozy.report_usage_entries WHERE report_id = :reportId")
        .param("reportId", reportId).query(Long.class).optional();
  }

  public void insert(long memberId, long reportId, LocalDate period, OffsetDateTime completedAt,
      boolean dataLabQuotaExhausted) {
    jdbc.sql("""
        INSERT INTO chozy.report_usage_entries
          (report_id, member_id, period_start, completed_at, data_lab_quota_exhausted)
        VALUES (:reportId, :memberId, :period, :completedAt, :exempt)
        """).param("reportId", reportId).param("memberId", memberId).param("period", period)
        .param("completedAt", completedAt).param("exempt", dataLabQuotaExhausted).update();
  }
}
