package com.chozy.server.favorite.repository;

import com.chozy.server.favorite.dto.Favorites.Counts;
import com.chozy.server.favorite.dto.Favorites.LatestSale;
import com.chozy.server.favorite.dto.Favorites.SaleResult;
import com.chozy.server.favorite.dto.Favorites.Status;
import com.chozy.server.report.dto.ReportDetail;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class FavoriteRepository {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final String ACTIVE_REPORT_FAVORITES = """
      SELECT id, report_id, product_name, status, wholesale_price, shipping_fee, expected_selling_price,
             profit_per_unit, margin_rate, saved_score, saved_supply_score, saved_market_score,
             created_at, updated_at, status_updated_at
      FROM chozy.member_favorites
      WHERE member_id = :member AND deleted_at IS NULL AND report_id IS NOT NULL
      """;

  private final JdbcClient jdbc;

  public FavoriteRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Counts counts(long memberId) {
    return jdbc.sql("""
        SELECT count(*) FILTER (WHERE status = 'INTERESTED') AS interested,
               count(*) FILTER (WHERE status = 'SELLING') AS selling
        FROM chozy.member_favorites WHERE member_id = :member AND deleted_at IS NULL
        """).param("member", memberId)
        .query((row, index) -> new Counts(row.getLong("interested"), row.getLong("selling"))).single();
  }

  public Optional<Entry> findByReport(long memberId, long reportId) {
    return jdbc.sql(ACTIVE_REPORT_FAVORITES + " AND report_id = :report")
        .param("member", memberId).param("report", reportId).query(FavoriteRepository::entry).optional();
  }

  public Optional<Entry> find(long memberId, long favoriteId) {
    return jdbc.sql(ACTIVE_REPORT_FAVORITES + " AND id = :favorite")
        .param("member", memberId).param("favorite", favoriteId).query(FavoriteRepository::entry).optional();
  }

  public List<Entry> list(long memberId, Status status) {
    return jdbc.sql(ACTIVE_REPORT_FAVORITES + " AND status = :status ORDER BY created_at DESC, id DESC")
        .param("member", memberId).param("status", status.name()).query(FavoriteRepository::entry).list();
  }

  public long insert(long memberId, ReportDetail report, Status status, OffsetDateTime now) {
    var profit = report.internalDiagnosis().profitability();
    var summary = report.summary();
    return jdbc.sql("""
        INSERT INTO chozy.member_favorites (
          member_id, report_id, product_name, keyword, status, wholesale_price, shipping_fee, expected_selling_price,
          profit_per_unit, margin_rate, saved_score, saved_supply_score, saved_market_score,
          created_at, updated_at, status_updated_at
        ) VALUES (
          :member, :report, :name, :keyword, :status, :price, :shipping, :selling,
          :profit, :margin, :score, :supply, :market, :now, :now, :now
        ) RETURNING id
        """).param("member", memberId).param("report", report.reportId()).param("name", report.product().productName())
        .param("keyword", report.externalDiagnosis().keyword()).param("status", status.name())
        .param("price", profit.wholesalePrice()).param("shipping", profit.shippingFee())
        .param("selling", profit.expectedSellingPrice()).param("profit", profit.profitPerUnit())
        .param("margin", profit.marginRate()).param("score", summary.score())
        .param("supply", summary.supplyConditionScore()).param("market", summary.marketConditionScore())
        .param("now", now).query(Long.class).single();
  }

  public void changeStatus(long memberId, long favoriteId, Status status, OffsetDateTime now) {
    jdbc.sql("""
        UPDATE chozy.member_favorites SET status = :status, status_updated_at = :now
        WHERE member_id = :member AND id = :favorite AND deleted_at IS NULL
        """).param("member", memberId).param("favorite", favoriteId).param("status", status.name())
        .param("now", now).update();
  }

  public boolean delete(long memberId, long favoriteId, OffsetDateTime now) {
    return jdbc.sql("""
        UPDATE chozy.member_favorites SET deleted_at = :now
        WHERE member_id = :member AND id = :favorite AND deleted_at IS NULL
        """).param("member", memberId).param("favorite", favoriteId).param("now", now).update() == 1;
  }

  public long recordSale(Entry favorite, SaleResult result, OffsetDateTime now) {
    return jdbc.sql("""
        INSERT INTO chozy.favorite_sales_results (favorite_id, result, score, supply_score, market_score, recorded_at)
        VALUES (:favorite, :result, :score, :supply, :market, :now) RETURNING id
        """).param("favorite", favorite.id()).param("result", result.name()).param("score", favorite.score())
        .param("supply", favorite.supplyScore()).param("market", favorite.marketScore()).param("now", now)
        .query(Long.class).single();
  }

  public Optional<LatestSale> latestSale(long favoriteId) {
    return jdbc.sql("""
        SELECT result, recorded_at FROM chozy.favorite_sales_results WHERE favorite_id = :favorite
        ORDER BY recorded_at DESC, id DESC LIMIT 1
        """).param("favorite", favoriteId)
        .query((row, index) -> new LatestSale(SaleResult.valueOf(row.getString("result")), time(row, "recorded_at")))
        .optional();
  }

  private static Entry entry(ResultSet row, int index) throws SQLException {
    return new Entry(row.getLong("id"), row.getLong("report_id"), row.getString("product_name"),
        Status.valueOf(row.getString("status")), row.getBigDecimal("wholesale_price"),
        row.getBigDecimal("shipping_fee"), row.getBigDecimal("expected_selling_price"),
        row.getBigDecimal("profit_per_unit"), row.getBigDecimal("margin_rate"), row.getBigDecimal("saved_score"),
        row.getBigDecimal("saved_supply_score"), row.getBigDecimal("saved_market_score"),
        time(row, "created_at"), time(row, "updated_at"), time(row, "status_updated_at"));
  }

  private static OffsetDateTime time(ResultSet row, String column) throws SQLException {
    return row.getObject(column, OffsetDateTime.class).atZoneSameInstant(KOREA).toOffsetDateTime();
  }

  public record Entry(long id, long reportId, String productName, Status status, BigDecimal wholesalePrice,
      BigDecimal shippingFee, BigDecimal expectedSellingPrice, BigDecimal profitPerUnit, BigDecimal marginRate,
      BigDecimal score, BigDecimal supplyScore, BigDecimal marketScore, OffsetDateTime createdAt,
      OffsetDateTime updatedAt, OffsetDateTime statusUpdatedAt) {}
}
