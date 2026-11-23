package com.chozy.server.notification.dto;

import com.chozy.server.common.response.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public final class Notifications {
  private Notifications() {}

  public enum Type { SCORE_CHANGE, SUPPLY_PRICE, STOCK_OUT, STOCK_RESTOCK, SEARCH_VOLUME, SYSTEM, NOTICE }
  public enum TargetType { FAVORITE }

  public record Item(long notificationId, Type type, String title, String message, boolean isRead,
      OffsetDateTime createdAt, TargetType targetType, @Schema(types = {"integer", "null"}) Long targetId) {}
  public record Listing(long unreadCount, List<Item> items, Pagination pagination) {}
  public record ReadResult(long notificationId, boolean isRead, long unreadCount) {}
  public record ReadAllResult(long updatedCount, long unreadCount) {}
}
