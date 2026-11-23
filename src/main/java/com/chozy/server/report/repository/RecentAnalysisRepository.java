package com.chozy.server.report.repository;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.AnalysisStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class RecentAnalysisRepository {
  private final JdbcClient jdbc;

  public RecentAnalysisRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long countOwned(long memberId) {
    return jdbc.sql("SELECT count(*) FROM chozy.manual_analysis_jobs WHERE member_id = :member")
        .param("member", memberId).query(Long.class).single();
  }

  public List<Entry> findOwned(long memberId, long offset, int size) {
    return jdbc.sql("""
        SELECT j.id AS analysis_id, j.status, j.source, j.product_id, r.id AS report_id,
               COALESCE(j.product_name, r.snapshot #>> '{product,productName}') AS product_name,
               CASE WHEN j.source <> 'MANUAL' THEN r.snapshot #>> '{product,productImageUrl}' END AS image_url,
               CASE WHEN j.status = 'COMPLETED' THEN j.completed_at END AS analyzed_at,
               CASE WHEN jsonb_exists(r.snapshot -> 'externalDiagnosis', 'keyword')
                    THEN r.snapshot #>> '{externalDiagnosis,keyword}' ELSE j.keyword END AS keyword,
               CAST(r.snapshot -> 'summary' AS text) AS summary
        FROM chozy.manual_analysis_jobs j
        LEFT JOIN chozy.manual_analysis_reports r ON r.analysis_id = j.id AND j.status = 'COMPLETED'
        WHERE j.member_id = :member
        ORDER BY COALESCE(j.completed_at, j.requested_at) DESC, j.id DESC
        LIMIT :size OFFSET :offset
        """).param("member", memberId).param("size", size).param("offset", offset)
        .query((row, index) -> new Entry(row.getLong("analysis_id"), row.getObject("report_id", Long.class),
            AnalysisSource.valueOf(row.getString("source")), row.getString("product_id"),
            row.getString("product_name"), row.getString("image_url"), row.getString("keyword"), row.getString("summary"),
            AnalysisStatus.valueOf(row.getString("status")), row.getObject("analyzed_at", OffsetDateTime.class)))
        .list();
  }

  public record Entry(long analysisId, Long reportId, AnalysisSource source, String productId, String productName,
      String imageUrl, String keyword,
      String summary, AnalysisStatus status, OffsetDateTime analyzedAt) {}
}
