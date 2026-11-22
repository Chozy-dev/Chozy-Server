package com.chozy.server.member.service;

import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.dto.Dashboard;
import com.chozy.server.member.dto.Dashboard.*;
import com.chozy.server.member.repository.MemberActivityRepository;
import com.chozy.server.usage.service.ReportUsageService;
import com.chozy.server.usage.service.UsageMonth;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class DashboardService {
  private final MemberAccess members;
  private final MemberActivityRepository activity;
  private final ReportUsageService reportUsage;
  private final Clock clock;

  public DashboardService(MemberAccess members, MemberActivityRepository activity,
      ReportUsageService reportUsage, Clock clock) {
    this.members = members;
    this.activity = activity;
    this.reportUsage = reportUsage;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Dashboard get(AuthenticatedMember principal) {
    var member = members.requireCurrent(principal);
    long id = member.getId();
    var now = clock.instant();
    var month = UsageMonth.at(now);
    var createdAt = member.getCreatedAt().atZone(UsageMonth.KOREA);
    long membershipDays = ChronoUnit.DAYS.between(createdAt.toLocalDate(),
        now.atZone(UsageMonth.KOREA).toLocalDate()) + 1;
    return new Dashboard(
        new Dashboard.Profile(id, member.getLoginId(), member.getNickname(), member.getEmail(),
            createdAt.toOffsetDateTime(), membershipDays),
        new Plan("FREE", "무료 플랜"),
        reportUsage.forMonth(id, month),
        new ScreeningUsage(month.period().toString(),
            activity.countScreeningRuns(id, month.startsAt(), month.nextResetAt()), true),
        new FavoriteUsage(activity.countFavorites(id), 10),
        MarketingConsentService.current(member));
  }
}
