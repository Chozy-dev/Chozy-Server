package com.chozy.server.member.dto;

import com.chozy.server.usage.service.ReportUsage;
import java.time.OffsetDateTime;

public record Dashboard(Profile profile, Plan plan, ReportUsage reportUsage, ScreeningUsage screeningUsage,
    FavoriteUsage favoriteUsage, MarketingConsent marketingConsent) {
  public record Profile(long userId, String loginId, String nickname, String email,
      OffsetDateTime createdAt, long membershipDays) {}

  public record Plan(String code, String name) {}

  public record ScreeningUsage(String period, long usedCount, boolean unlimited) {}

  public record FavoriteUsage(long usedCount, int limitCount) {}
}
