package com.chozy.server.support;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;

public record ReportDataFixture(JdbcClient jdbc) {
  public long analysis(long owner, String product, String status, String finishedAt) {
    var completedAt = finishedAt == null ? null : OffsetDateTime.parse(finishedAt);
    return jdbc.sql("""
        INSERT INTO chozy.manual_analysis_jobs
          (member_id, product_name, wholesale_price, shipping_fee, has_price_policy, keyword, requested_at,
           status, completed_at, error_message)
        VALUES (:owner, :product, 8000, 0, false, 'saved keyword', '2026-11-23T01:00:00Z'::timestamptz,
                :status, :completed, :error)
        RETURNING id
        """).param("owner", owner).param("product", product).param("status", status)
        .param("completed", completedAt).param("error", status.equals("FAILED") ? "Fixture error" : null)
        .query(Long.class).single();
  }

  public long report(long analysisId, String snapshot) {
    return jdbc.sql("""
        INSERT INTO chozy.manual_analysis_reports (analysis_id, snapshot)
        VALUES (:analysis, CAST(:snapshot AS jsonb)) RETURNING id
        """).param("analysis", analysisId).param("snapshot", snapshot).query(Long.class).single();
  }
}
