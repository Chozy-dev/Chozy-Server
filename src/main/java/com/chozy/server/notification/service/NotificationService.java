package com.chozy.server.notification.service;

import static com.chozy.server.notification.dto.NotificationSettings.PushStatus.COMING_SOON;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.response.Pagination;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.notification.dto.NotificationSettings;
import com.chozy.server.notification.dto.NotificationSettings.EventTypes;
import com.chozy.server.notification.dto.NotificationSettings.Patch;
import com.chozy.server.notification.dto.NotificationSettings.Thresholds;
import com.chozy.server.notification.dto.Notifications.*;
import com.chozy.server.notification.repository.NotificationRepository;
import com.chozy.server.notification.repository.NotificationSettingsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class NotificationService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final NotificationSettings DEFAULTS = new NotificationSettings(true, true, COMING_SOON,
      new EventTypes(true, true, true, true, true), new Thresholds(10, 5, 30), null);
  private static final Set<Integer> SCORE_CHANGE_OPTIONS = Set.of(5, 10, 15);
  private static final Set<Integer> SEARCH_INCREASE_OPTIONS = Set.of(30, 50, 100);

  private final MemberAccess members;
  private final NotificationSettingsRepository settings;
  private final NotificationRepository notifications;
  private final Clock clock;

  public NotificationService(MemberAccess members, NotificationSettingsRepository settings,
      NotificationRepository notifications, Clock clock) {
    this.members = members;
    this.settings = settings;
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public NotificationSettings settings(AuthenticatedMember member) {
    return currentSettings(members.requireCurrent(member).getId());
  }

  @Transactional
  public NotificationSettings updateSettings(AuthenticatedMember member, Patch patch) {
    if (!patch.hasValues()) throw new BusinessException(ErrorCode.INVALID_INPUT);
    long memberId = members.lockCurrent(member).getId();
    var current = currentSettings(memberId);
    EventTypes events = current.eventTypes();
    if (patch.getEventTypes() != null) {
      var update = patch.getEventTypes();
      events = new EventTypes(Objects.requireNonNullElse(update.getSupplyPriceEnabled(), events.supplyPriceEnabled()),
          Objects.requireNonNullElse(update.getScoreChangeEnabled(), events.scoreChangeEnabled()),
          Objects.requireNonNullElse(update.getStockOutEnabled(), events.stockOutEnabled()),
          Objects.requireNonNullElse(update.getRestockEnabled(), events.restockEnabled()),
          Objects.requireNonNullElse(update.getSearchVolumeEnabled(), events.searchVolumeEnabled()));
    }
    Thresholds thresholds = current.thresholds();
    if (patch.getThresholds() != null) {
      var update = patch.getThresholds();
      thresholds = new Thresholds(
          Objects.requireNonNullElse(update.getSupplyPriceChangePercent(), thresholds.supplyPriceChangePercent()),
          Objects.requireNonNullElse(update.getScoreChangePoints(), thresholds.scoreChangePoints()),
          Objects.requireNonNullElse(update.getSearchVolumeIncreasePercent(), thresholds.searchVolumeIncreasePercent()));
      if (!SCORE_CHANGE_OPTIONS.contains(thresholds.scoreChangePoints())
          || !SEARCH_INCREASE_OPTIONS.contains(thresholds.searchVolumeIncreasePercent())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT);
      }
    }
    var updated = new NotificationSettings(true, Objects.requireNonNullElse(patch.getEmailEnabled(), current.emailEnabled()),
        COMING_SOON, events, thresholds, now());
    settings.save(memberId, updated);
    return updated;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Listing list(AuthenticatedMember member, Boolean isRead, int page, int size) {
    long memberId = members.requireCurrent(member).getId();
    long unread = notifications.count(memberId, false);
    long total = Boolean.FALSE.equals(isRead) ? unread : notifications.count(memberId, isRead);
    long pageIndex = page - 1L;
    long offset = pageIndex * size;
    var items = offset < total ? notifications.list(memberId, isRead, offset, size) : List.<Item>of();
    return new Listing(unread, items, Pagination.of(page, pageIndex, size, total));
  }

  @Transactional
  public ReadResult markRead(AuthenticatedMember member, long notificationId) {
    long memberId = members.lockCurrent(member).getId();
    if (!notifications.markRead(memberId, notificationId, now())) throw new BusinessException(ErrorCode.NOT_FOUND);
    return new ReadResult(notificationId, true, notifications.count(memberId, false));
  }

  @Transactional
  public ReadAllResult markAllRead(AuthenticatedMember member) {
    long memberId = members.lockCurrent(member).getId();
    int updated = notifications.markAllRead(memberId, now());
    return new ReadAllResult(updated, notifications.count(memberId, false));
  }

  private NotificationSettings currentSettings(long memberId) {
    return settings.find(memberId).orElse(DEFAULTS);
  }

  private OffsetDateTime now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
  }
}
