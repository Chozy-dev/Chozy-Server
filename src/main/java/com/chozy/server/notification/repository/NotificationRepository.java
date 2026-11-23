package com.chozy.server.notification.repository;

import com.chozy.server.notification.dto.Notifications.Item;
import com.chozy.server.notification.dto.Notifications.TargetType;
import com.chozy.server.notification.dto.Notifications.Type;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class NotificationRepository {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final String OWNED_NOTIFICATIONS = " FROM chozy.member_notifications WHERE member_id = :member";

  private final JdbcClient jdbc;

  public NotificationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long count(long memberId, Boolean isRead) {
    return filtered("SELECT count(*)", memberId, isRead, "").query(Long.class).single();
  }

  public List<Item> list(long memberId, Boolean isRead, long offset, int size) {
    return filtered("""
        SELECT id, type, title, message, favorite_id, read_at IS NOT NULL AS is_read, created_at
        """, memberId, isRead, " ORDER BY (read_at IS NOT NULL), created_at DESC, id DESC LIMIT :size OFFSET :offset")
        .param("size", size).param("offset", offset).query((row, index) -> {
          Long favoriteId = row.getObject("favorite_id", Long.class);
          return new Item(row.getLong("id"), Type.valueOf(row.getString("type")), row.getString("title"),
              row.getString("message"), row.getBoolean("is_read"),
              row.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(KOREA).toOffsetDateTime(),
              favoriteId == null ? null : TargetType.FAVORITE, favoriteId);
        }).list();
  }

  public boolean markRead(long memberId, long notificationId, OffsetDateTime now) {
    int updated = jdbc.sql("""
        UPDATE chozy.member_notifications SET read_at = :now
        WHERE member_id = :member AND id = :id AND read_at IS NULL
        """).param("member", memberId).param("id", notificationId).param("now", now).update();
    return updated == 1 || jdbc.sql("SELECT id" + OWNED_NOTIFICATIONS + " AND id = :id")
        .param("member", memberId).param("id", notificationId).query(Long.class).optional().isPresent();
  }

  public int markAllRead(long memberId, OffsetDateTime now) {
    return jdbc.sql("""
        UPDATE chozy.member_notifications SET read_at = :now WHERE member_id = :member AND read_at IS NULL
        """).param("member", memberId).param("now", now).update();
  }

  private JdbcClient.StatementSpec filtered(String selection, long memberId, Boolean isRead, String suffix) {
    String filter = isRead == null ? "" : " AND (read_at IS NOT NULL) = :isRead";
    var statement = jdbc.sql(selection + OWNED_NOTIFICATIONS + filter + suffix).param("member", memberId);
    return isRead == null ? statement : statement.param("isRead", isRead);
  }
}
