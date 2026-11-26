package com.chozy.server.home.repository;

import static com.chozy.server.common.error.ErrorCode.COUPANG_BEST_UNAVAILABLE;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestCategory;
import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestProduct;
import com.chozy.server.home.dto.CoupangBestProducts.RankStatus;
import com.chozy.server.home.service.CoupangBestReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CollectorCoupangBestReader implements CoupangBestReader {
  private static final String QUERY = """
      WITH latest AS (
        SELECT snapshot_date, published_at
        FROM public.coupang_best_batches WHERE status = 'completed'
        ORDER BY snapshot_date DESC LIMIT 1
      )
      SELECT l.published_at, c.category_id, c.category_name, p.rank, p.product_id,
        p.product_name, p.price, p.is_rocket, p.previous_rank, p.rank_change, p.rank_status
      FROM latest l
      LEFT JOIN public.coupang_best_categories c ON c.snapshot_date = l.snapshot_date AND c.status = 'completed'
      LEFT JOIN public.coupang_best_published p ON p.snapshot_date = l.snapshot_date AND p.category_id = c.category_id
      ORDER BY c.category_id, p.rank
      """;

  private final JdbcTemplate jdbc;

  public CollectorCoupangBestReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Publication> latest() {
    try {
      return jdbc.query(QUERY, CollectorCoupangBestReader::readPublication);
    } catch (DataAccessException | IllegalArgumentException ex) {
      throw new BusinessException(COUPANG_BEST_UNAVAILABLE);
    }
  }

  private static Optional<Publication> readPublication(ResultSet rows) throws SQLException {
    if (!rows.next()) return Optional.empty();
    var publishedAt = rows.getObject("published_at", OffsetDateTime.class);
    var categories = new LinkedHashMap<CoupangBestCategory, List<CoupangBestProduct>>();
    do {
      var categoryId = rows.getObject("category_id", Long.class);
      if (categoryId == null) continue;
      var category = new CoupangBestCategory(categoryId, rows.getString("category_name"));
      var items = categories.computeIfAbsent(category, key -> new ArrayList<>());
      var productId = rows.getObject("product_id", Long.class);
      if (productId == null) continue;
      var rankStatus = rows.getString("rank_status");
      items.add(new CoupangBestProduct(rows.getInt("rank"), productId, rows.getString("product_name"),
          rows.getBigDecimal("price"), rows.getBoolean("is_rocket"),
          rows.getObject("previous_rank", Integer.class), rows.getObject("rank_change", Integer.class),
          rankStatus == null ? null : RankStatus.valueOf(rankStatus)));
    } while (rows.next());
    return Optional.of(new Publication(publishedAt, categories.entrySet().stream()
        .map(entry -> new PublishedCategory(entry.getKey(), entry.getValue())).toList()));
  }
}
