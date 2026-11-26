package com.chozy.server.home.repository;

import static com.chozy.server.common.error.ErrorCode.NAVER_SEASON_UNAVAILABLE;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonKeyword;
import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonTheme;
import com.chozy.server.home.service.NaverSeasonReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CollectorNaverSeasonReader implements NaverSeasonReader {
  private static final String LATEST = """
      WITH latest AS (
        SELECT snapshot_date, published_at
        FROM public.naver_season_batches WHERE status = 'completed'
        ORDER BY snapshot_date DESC LIMIT 1
      )
      """;
  private static final String FIELDS = """
      SELECT l.snapshot_date, l.published_at, t.theme_code, t.theme_name,
        i.rank, i.keyword, i.monthly_search_count, i.growth_rate,
      """;
  private static final String THEMES = """
      FROM latest l
      LEFT JOIN public.naver_season_themes t ON t.snapshot_date = l.snapshot_date
      LEFT JOIN public.naver_season_published i ON i.snapshot_date = l.snapshot_date AND i.theme_code = t.theme_code
      """;
  private static final String ORDER = "ORDER BY t.theme_code, i.rank";
  private static final String SEASON_QUERY = LATEST + FIELDS
      + "NULL::timestamptz AS coupang_published_at, NULL::bigint AS matched_product_count\n" + THEMES + ORDER;
  private static final String COMPARISON_QUERY = LATEST + """
      , coupang AS (
        SELECT b.snapshot_date, b.published_at
        FROM public.coupang_best_batches b JOIN latest l USING (snapshot_date)
        WHERE b.status = 'completed' AND EXISTS (
          SELECT 1 FROM public.coupang_best_published p WHERE p.snapshot_date = b.snapshot_date
        )
      ), product_names AS (
        SELECT p.product_id, regexp_replace(p.product_name, '[[:space:]]', '', 'g') AS normalized_name
        FROM public.coupang_best_published p JOIN coupang c USING (snapshot_date)
      )
      """ + FIELDS + """
        c.published_at AS coupang_published_at,
        CASE WHEN c.snapshot_date IS NOT NULL AND i.keyword IS NOT NULL THEN (
          SELECT count(DISTINCT p.product_id) FROM product_names p
          WHERE position(regexp_replace(i.keyword, '[[:space:]]', '', 'g') IN p.normalized_name) > 0
        ) END AS matched_product_count
      """ + THEMES + "LEFT JOIN coupang c ON c.snapshot_date = l.snapshot_date\n" + ORDER;

  private final JdbcTemplate jdbc;

  public CollectorNaverSeasonReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Publication> latest(boolean includeComparison) {
    try {
      return jdbc.query(includeComparison ? COMPARISON_QUERY : SEASON_QUERY, CollectorNaverSeasonReader::readPublication);
    } catch (DataAccessException ex) {
      throw new BusinessException(NAVER_SEASON_UNAVAILABLE);
    }
  }

  private static Optional<Publication> readPublication(ResultSet rows) throws SQLException {
    if (!rows.next()) return Optional.empty();
    var snapshotDate = rows.getObject("snapshot_date", LocalDate.class);
    var publishedAt = rows.getObject("published_at", OffsetDateTime.class);
    var coupangPublishedAt = rows.getObject("coupang_published_at", OffsetDateTime.class);
    var comparison = coupangPublishedAt == null ? null : new ComparedPublication(snapshotDate, coupangPublishedAt);
    var themes = new LinkedHashMap<SeasonTheme, List<PublishedKeyword>>();
    do {
      var themeCode = rows.getObject("theme_code", Integer.class);
      if (themeCode == null) continue;
      var theme = new SeasonTheme(themeCode, rows.getString("theme_name"));
      var items = themes.computeIfAbsent(theme, key -> new ArrayList<>());
      var keyword = rows.getString("keyword");
      if (keyword == null) continue;
      items.add(new PublishedKeyword(new SeasonKeyword(rows.getInt("rank"), keyword,
          rows.getLong("monthly_search_count"), rows.getBigDecimal("growth_rate")),
          rows.getObject("matched_product_count", Long.class)));
    } while (rows.next());
    return Optional.of(new Publication(snapshotDate, publishedAt, themes.entrySet().stream()
        .map(entry -> new PublishedTheme(entry.getKey(), entry.getValue())).toList(), comparison));
  }
}
