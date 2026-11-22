package com.chozy.server.analysis.repository;

import com.chozy.server.analysis.dto.AnalysisStatus;
import com.chozy.server.analysis.dto.ManualAnalysisInput;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class ManualAnalysisRepository {
  private final JdbcClient jdbc;

  public ManualAnalysisRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long enqueue(long memberId, ManualAnalysisInput input, OffsetDateTime now) {
    return jdbc.sql("""
        INSERT INTO chozy.manual_analysis_jobs (member_id, product_name, wholesale_price, shipping_fee,
          has_price_policy, minimum_selling_price, keyword, requested_at)
        VALUES (:member, :name, :price, :shipping, :policy, :minimum, :keyword, :now) RETURNING id
        """)
        .param("member", memberId).param("name", input.productName())
        .param("price", input.wholesalePrice()).param("shipping", input.shippingFee())
        .param("policy", input.hasPricePolicy()).param("minimum", input.minimumSellingPrice())
        .param("keyword", input.keyword()).param("now", now).query(Long.class).single();
  }

  public long activeCount(long memberId) {
    return jdbc.sql("""
        SELECT count(*) FROM chozy.manual_analysis_jobs
        WHERE member_id = :member AND status IN ('PENDING', 'PROCESSING')
        """).param("member", memberId).query(Long.class).single();
  }

  public Optional<Job> findOwned(long id, long memberId) {
    return jdbc.sql("""
        SELECT j.*, r.id AS report_id FROM chozy.manual_analysis_jobs j
        LEFT JOIN chozy.manual_analysis_reports r ON r.analysis_id = j.id
        WHERE j.id = :id AND j.member_id = :member
        """).param("id", id).param("member", memberId).query((row, index) -> mapJob(row)).optional();
  }

  public Optional<Job> claim(OffsetDateTime leaseUntil) {
    return jdbc.sql("""
        WITH candidate AS (
          SELECT id FROM chozy.manual_analysis_jobs WHERE status = 'PENDING'
          ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
        )
        UPDATE chozy.manual_analysis_jobs j
        SET status = 'PROCESSING', lease_until = :lease
        FROM candidate WHERE j.id = candidate.id
        RETURNING j.*, NULL::bigint AS report_id
        """).param("lease", leaseUntil).query((row, index) -> mapJob(row)).optional();
  }

  public boolean complete(long id, OffsetDateTime now) {
    return jdbc.sql("""
        UPDATE chozy.manual_analysis_jobs SET status = 'COMPLETED', lease_until = NULL, completed_at = :now
        WHERE id = :id AND status = 'PROCESSING' AND lease_until > :now
        """).param("id", id).param("now", now).update() == 1;
  }

  public void fail(long id, OffsetDateTime now, String message) {
    jdbc.sql("""
        UPDATE chozy.manual_analysis_jobs
        SET status = 'FAILED', lease_until = NULL, completed_at = :now, error_message = :message
        WHERE id = :id AND status = 'PROCESSING'
        """).param("id", id).param("now", now).param("message", message).update();
  }

  public void expire(OffsetDateTime now, String message) {
    jdbc.sql("""
        UPDATE chozy.manual_analysis_jobs
        SET status = 'FAILED', lease_until = NULL, completed_at = :now, error_message = :message
        WHERE status = 'PROCESSING' AND lease_until <= :now
        """).param("now", now).param("message", message).update();
  }

  public long saveReport(long analysisId, String snapshot) {
    return jdbc.sql("""
        INSERT INTO chozy.manual_analysis_reports (analysis_id, snapshot)
        VALUES (:analysis, CAST(:snapshot AS jsonb)) RETURNING id
        """).param("analysis", analysisId).param("snapshot", snapshot).query(Long.class).single();
  }

  public Optional<String> findReport(long reportId, long memberId) {
    return jdbc.sql("""
        SELECT CAST(r.snapshot AS text) FROM chozy.manual_analysis_reports r
        JOIN chozy.manual_analysis_jobs j ON j.id = r.analysis_id
        WHERE r.id = :report AND j.member_id = :member AND j.status = 'COMPLETED'
        """).param("report", reportId).param("member", memberId).query(String.class).optional();
  }

  private static Job mapJob(ResultSet row) throws SQLException {
    var input = new ManualAnalysisInput(row.getString("product_name"), row.getBigDecimal("wholesale_price"),
        row.getBigDecimal("shipping_fee"), row.getBoolean("has_price_policy"),
        row.getBigDecimal("minimum_selling_price"), row.getString("keyword"));
    return new Job(row.getLong("id"), row.getLong("member_id"), input,
        AnalysisStatus.valueOf(row.getString("status")), row.getObject("report_id", Long.class),
        row.getObject("completed_at", OffsetDateTime.class), row.getString("error_message"));
  }

  public record Job(long id, long memberId, ManualAnalysisInput input, AnalysisStatus status,
                    Long reportId, OffsetDateTime completedAt, String errorMessage) {}
}
