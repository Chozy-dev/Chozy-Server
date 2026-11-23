package com.chozy.server.notification.repository;

import static com.chozy.server.notification.dto.NotificationSettings.PushStatus.COMING_SOON;

import com.chozy.server.notification.dto.NotificationSettings;
import com.chozy.server.notification.dto.NotificationSettings.EventTypes;
import com.chozy.server.notification.dto.NotificationSettings.Thresholds;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class NotificationSettingsRepository {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final JdbcClient jdbc;

  public NotificationSettingsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<NotificationSettings> find(long memberId) {
    return jdbc.sql("""
        SELECT email_enabled, supply_price_enabled, score_change_enabled, stock_out_enabled, restock_enabled,
               search_volume_enabled, supply_price_change_percent, score_change_points,
               search_volume_increase_percent, updated_at
        FROM chozy.member_notification_settings WHERE member_id = :member
        """).param("member", memberId).query((row, index) -> new NotificationSettings(
        true, row.getBoolean("email_enabled"), COMING_SOON,
        new EventTypes(row.getBoolean("supply_price_enabled"), row.getBoolean("score_change_enabled"),
            row.getBoolean("stock_out_enabled"), row.getBoolean("restock_enabled"),
            row.getBoolean("search_volume_enabled")),
        new Thresholds(row.getInt("supply_price_change_percent"), row.getInt("score_change_points"),
            row.getInt("search_volume_increase_percent")),
        row.getObject("updated_at", OffsetDateTime.class).atZoneSameInstant(KOREA).toOffsetDateTime())).optional();
  }

  public void save(long memberId, NotificationSettings settings) {
    var events = settings.eventTypes();
    var thresholds = settings.thresholds();
    jdbc.sql("""
        INSERT INTO chozy.member_notification_settings (
          member_id, email_enabled, supply_price_enabled, score_change_enabled, stock_out_enabled,
          restock_enabled, search_volume_enabled, supply_price_change_percent, score_change_points,
          search_volume_increase_percent, updated_at
        ) VALUES (:member, :email, :supply, :score, :stock, :restock, :search, :supplyPct, :points, :searchPct, :now)
        ON CONFLICT (member_id) DO UPDATE SET
          email_enabled = EXCLUDED.email_enabled, supply_price_enabled = EXCLUDED.supply_price_enabled,
          score_change_enabled = EXCLUDED.score_change_enabled, stock_out_enabled = EXCLUDED.stock_out_enabled,
          restock_enabled = EXCLUDED.restock_enabled, search_volume_enabled = EXCLUDED.search_volume_enabled,
          supply_price_change_percent = EXCLUDED.supply_price_change_percent,
          score_change_points = EXCLUDED.score_change_points,
          search_volume_increase_percent = EXCLUDED.search_volume_increase_percent, updated_at = EXCLUDED.updated_at
        """).param("member", memberId).param("email", settings.emailEnabled())
        .param("supply", events.supplyPriceEnabled()).param("score", events.scoreChangeEnabled())
        .param("stock", events.stockOutEnabled()).param("restock", events.restockEnabled())
        .param("search", events.searchVolumeEnabled()).param("supplyPct", thresholds.supplyPriceChangePercent())
        .param("points", thresholds.scoreChangePoints()).param("searchPct", thresholds.searchVolumeIncreasePercent())
        .param("now", settings.updatedAt()).update();
  }
}
