package com.chozy.server.report.repository;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.report.dto.ReportFeedback.Opinion;
import com.chozy.server.report.dto.ReportSummary;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class ReportRepository {
  private final JdbcClient jdbc;

  public ReportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<StoredReport> findOwned(long reportId, long memberId) {
    return jdbc.sql("""
        SELECT r.id AS report_id, j.id AS analysis_id, j.completed_at, CAST(r.snapshot AS text) AS snapshot,
               j.product_name, j.wholesale_price, j.shipping_fee, j.has_price_policy, j.minimum_selling_price,
               j.keyword, j.source
        FROM chozy.manual_analysis_reports r
        JOIN chozy.manual_analysis_jobs j ON j.id = r.analysis_id
        WHERE r.id = :report AND j.member_id = :member AND j.status = 'COMPLETED'
        """).param("report", reportId).param("member", memberId)
        .query((row, index) -> new StoredReport(
            row.getLong("report_id"), row.getLong("analysis_id"), row.getObject("completed_at", OffsetDateTime.class),
            row.getString("snapshot"), row.getString("product_name"), row.getBigDecimal("wholesale_price"),
            row.getBigDecimal("shipping_fee"), row.getBoolean("has_price_policy"),
            row.getBigDecimal("minimum_selling_price"), row.getString("keyword"),
            AnalysisSource.valueOf(row.getString("source"))))
        .optional();
  }

  public Optional<Long> insertFeedback(long reportId, String productId, ReportSummary summary, Opinion feedback,
      OffsetDateTime createdAt) {
    return jdbc.sql("""
        INSERT INTO chozy.report_feedback
          (report_id, product_id, score, supply_condition_score, market_condition_score, feedback, created_at)
        VALUES (:report, :product, :score, :supply, :market, :feedback, :now)
        ON CONFLICT (report_id) DO NOTHING
        RETURNING id
        """)
        .param("report", reportId).param("product", productId)
        .param("score", summary.score()).param("supply", summary.supplyConditionScore())
        .param("market", summary.marketConditionScore()).param("feedback", feedback.name()).param("now", createdAt)
        .query(Long.class).optional();
  }

  public boolean updateSnapshot(StoredReport current, String updated) {
    return jdbc.sql("""
        UPDATE chozy.manual_analysis_reports
        SET snapshot = CAST(:updated AS jsonb)
        WHERE id = :report AND snapshot = CAST(:previous AS jsonb)
        """).param("report", current.reportId()).param("previous", current.snapshot()).param("updated", updated)
        .update() == 1;
  }

  public record StoredReport(long reportId, long analysisId, OffsetDateTime completedAt, String snapshot,
      String productName, BigDecimal wholesalePrice, BigDecimal shippingFee, boolean hasPricePolicy,
      BigDecimal minimumSellingPrice, String keyword, AnalysisSource source) {}
}
