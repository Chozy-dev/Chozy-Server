package com.chozy.server.member.service;

import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.dto.MarketingConsent;
import com.chozy.server.member.entity.Member;
import com.chozy.server.member.repository.MarketingConsentHistoryRepository;
import com.chozy.server.usage.service.UsageMonth;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class MarketingConsentService {
  private final MemberAccess members;
  private final MarketingConsentHistoryRepository history;
  private final Clock clock;

  public MarketingConsentService(MemberAccess members, MarketingConsentHistoryRepository history, Clock clock) {
    this.members = members;
    this.history = history;
    this.clock = clock;
  }

  @Transactional
  public MarketingConsent change(AuthenticatedMember principal, boolean agreed) {
    var member = members.lockCurrent(principal);
    if (member.isMarketingAgreed() != agreed) {
      var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
      member.changeMarketingConsent(agreed, now);
      history.append(member.getId(), agreed, now.atZone(UsageMonth.KOREA).toOffsetDateTime());
    }
    return current(member);
  }

  static MarketingConsent current(Member member) {
    var updatedAt = member.getMarketingUpdatedAt();
    return new MarketingConsent(member.isMarketingAgreed(),
        updatedAt == null ? null : updatedAt.atZone(UsageMonth.KOREA).toOffsetDateTime());
  }
}
