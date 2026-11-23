package com.chozy.server.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.AccountPostgresITSupport;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class NotificationDatabaseIT extends AccountPostgresITSupport {
  private static final String SETTINGS = "/my/notification-settings";
  private static final String NOTIFICATIONS = "/home/notifications";

  @Test
  void mergesPartialSettingsAndRejectsInvalidSettingsWithoutAffectingOtherMembers() throws Exception {
    var owner = account();
    var other = account();
    authenticate(get(SETTINGS), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inAppEnabled").value(true))
        .andExpect(jsonPath("$.data.pushStatus").value("COMING_SOON"))
        .andExpect(jsonPath("$.data.emailEnabled").value(true))
        .andExpect(jsonPath("$.data.updatedAt").isEmpty());
    update(owner, """
        {"emailEnabled":false,"eventTypes":{"restockEnabled":false},"thresholds":{"scoreChangePoints":15}}
        """).andExpect(status().isOk()).andExpect(jsonPath("$.data.emailEnabled").value(false))
        .andExpect(jsonPath("$.data.eventTypes.restockEnabled").value(false))
        .andExpect(jsonPath("$.data.eventTypes.stockOutEnabled").value(true));
    update(owner, """
        {"thresholds":{"supplyPriceChangePercent":100,"searchVolumeIncreasePercent":50}}
        """).andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholds.supplyPriceChangePercent").value(100))
        .andExpect(jsonPath("$.data.thresholds.scoreChangePoints").value(15))
        .andExpect(jsonPath("$.data.emailEnabled").value(false));
    update(owner, "{\"eventTypes\":{}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    update(owner, "{\"thresholds\":{\"scoreChangePoints\":8}}").andExpect(status().isBadRequest());
    update(owner, "{\"thresholds\":{\"supplyPriceChangePercent\":0}}").andExpect(status().isBadRequest());
    update(owner, "{\"thresholds\":{\"searchVolumeIncreasePercent\":30.5}}").andExpect(status().isBadRequest());
    update(owner, "{\"emailEnabled\":null,\"eventTypes\":{\"restockEnabled\":true}}").andExpect(status().isBadRequest());
    authenticate(get(SETTINGS), owner).andExpect(jsonPath("$.data.eventTypes.restockEnabled").value(false))
        .andExpect(jsonPath("$.data.thresholds.searchVolumeIncreasePercent").value(50));
    authenticate(get(SETTINGS), other).andExpect(jsonPath("$.data.emailEnabled").value(true))
        .andExpect(jsonPath("$.data.thresholds.scoreChangePoints").value(5));
  }

  @Test
  void listsUnreadFirstAndMarksReadIdempotentlyWithinMemberScope() throws Exception {
    var owner = account();
    var other = account();
    long olderUnread = notification(owner, "2026-11-20T03:00:00Z", false);
    long newerUnread = notification(owner, "2026-11-21T03:00:00Z", false);
    long alreadyRead = notification(owner, "2026-11-23T03:00:00Z", true);
    long foreign = notification(other, "2026-11-23T03:00:00Z", false);
    authenticate(get(NOTIFICATIONS).param("size", "1"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].notificationId").value(newerUnread))
        .andExpect(jsonPath("$.data.items[0].isRead").value(false))
        .andExpect(jsonPath("$.data.unreadCount").value(2))
        .andExpect(jsonPath("$.data.pagination.page").value(1))
        .andExpect(jsonPath("$.data.pagination.totalPages").value(3));
    authenticate(get(NOTIFICATIONS).param("isRead", "true"), owner)
        .andExpect(jsonPath("$.data.items[0].notificationId").value(alreadyRead))
        .andExpect(jsonPath("$.data.items[0].targetType").isEmpty())
        .andExpect(jsonPath("$.data.unreadCount").value(2))
        .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
    authenticate(get(NOTIFICATIONS).param("page", "0"), owner).andExpect(status().isBadRequest());
    authenticate(patch(NOTIFICATIONS + "/" + foreign + "/read"), owner).andExpect(status().isNotFound());
    String read = NOTIFICATIONS + "/" + olderUnread + "/read";
    authenticate(patch(read), owner).andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
    var readAt = jdbc.sql("SELECT read_at FROM chozy.member_notifications WHERE id = :id")
        .param("id", olderUnread).query(OffsetDateTime.class).single();
    authenticate(patch(read), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.isRead").value(true)).andExpect(jsonPath("$.data.unreadCount").value(1));
    assertThat(jdbc.sql("SELECT read_at FROM chozy.member_notifications WHERE id = :id")
        .param("id", olderUnread).query(OffsetDateTime.class).single()).isEqualTo(readAt);
    authenticate(patch(NOTIFICATIONS + "/read-all"), owner).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedCount").value(1)).andExpect(jsonPath("$.data.unreadCount").value(0));
    authenticate(patch(NOTIFICATIONS + "/read-all"), owner).andExpect(jsonPath("$.data.updatedCount").value(0));
    authenticate(get(NOTIFICATIONS), other).andExpect(jsonPath("$.data.unreadCount").value(1));
  }

  private long notification(Account owner, String created, boolean read) {
    var time = OffsetDateTime.parse(created);
    return jdbc.sql("""
        INSERT INTO chozy.member_notifications (member_id, type, title, message, created_at, read_at)
        VALUES (:owner, 'SYSTEM', 'Title', 'Message', :created, :read) RETURNING id
        """).param("owner", owner.id()).param("created", time).param("read", read ? time : null)
        .query(Long.class).single();
  }

  private ResultActions update(Account owner, String patchJson) throws Exception {
    return authenticate(patch(SETTINGS).contentType(MediaType.APPLICATION_JSON).content(patchJson), owner);
  }

  private ResultActions authenticate(MockHttpServletRequestBuilder request, Account account) throws Exception {
    return mvc.perform(request.header("Authorization", account.bearer()));
  }
}
