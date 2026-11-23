package com.chozy.server.screening.service;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.screening.dto.Screenings.*;
import com.chozy.server.screening.repository.ScreeningRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile({"database", "dbdev"})
public class ScreeningService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final int MAX_KEYWORDS = 10;
  private static final int RECENT_KEYWORDS = 4;
  private static final Duration GUEST_LIFETIME = Duration.ofHours(24);
  private static final String ID_PREFIX = "scr_";

  private final ScreeningRepository repository;
  private final MemberAccess members;
  private final ObjectProvider<ScreeningDataProvider> data;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public ScreeningService(ScreeningRepository repository, MemberAccess members,
      ObjectProvider<ScreeningDataProvider> data, TransactionTemplate transactions, Clock clock) {
    this.repository = repository;
    this.members = members;
    this.data = data;
    this.transactions = transactions;
    this.clock = clock;
  }

  public ScreeningResult screen(AuthenticatedMember member, ScreeningRequest request) {
    var requested = request.keywords().stream().map(String::strip).distinct().toList();
    var selected = requested.stream().limit(MAX_KEYWORDS).toList();
    var end = now().toLocalDate().minusDays(1);
    var batch = provider().search(selected, end);
    var keywords = selected.stream().map(keyword -> {
      var metrics = batch.keywords().get(keyword);
      if (metrics == null || metrics.ads() == null) throw unavailable();
      return ScreeningScoring.keyword(keyword, metrics, end);
    }).toList();

    var at = now();
    var id = UUID.randomUUID();
    transactions.executeWithoutResult(status -> {
      if (member == null) {
        repository.deleteExpiredGuests(at);
        repository.save(id, null, at, end, at.plus(GUEST_LIFETIME), keywords);
      } else {
        long memberId = members.lockCurrent(member).getId();
        long memberRunId = repository.recordMemberRun(memberId, at);
        repository.save(id, memberRunId, at, end, null, keywords);
      }
    });
    return new ScreeningResult(ID_PREFIX + id, at, keywords.size(), requested.size() - selected.size(), keywords,
        batch.notice());
  }

  @Transactional(readOnly = true)
  public RecentScreenings recent(AuthenticatedMember member) {
    long memberId = members.requireCurrent(member).getId();
    return repository.latest(memberId).map(result -> new RecentScreenings(result.keywords().stream()
        .sorted(Comparator.comparingInt(Keyword::score).reversed()).limit(RECENT_KEYWORDS)
        .map(keyword -> new RecentItem(ID_PREFIX + result.id(), keyword.keyword(), keyword.score(),
            keyword.monthlySearchCount(), keyword.competitionLevel(), result.analyzedAt())).toList()))
        .orElseGet(() -> new RecentScreenings(List.of()));
  }

  public ShoppingResult shopping(AuthenticatedMember member, String screeningId, ShoppingRequest request) {
    var category = ShoppingCategories.require(request.categoryCode());
    Long memberId = member == null ? null : members.requireCurrent(member).getId();
    var stored = repository.findAccessible(parseId(screeningId), memberId, now())
        .orElseThrow(ScreeningService::notFound);
    var keyword = stored.keywords().stream().filter(item -> item.keyword().equals(request.keyword())).findFirst()
        .orElseThrow(ScreeningService::notFound);
    var end = stored.trendEndDate();
    var daily = provider().shopping(keyword.keyword(), category.categoryCode(), end);
    return ScreeningScoring.shopping(keyword, category, daily, end);
  }

  private ScreeningDataProvider provider() {
    var provider = data.getIfAvailable();
    if (provider == null) throw unavailable();
    return provider;
  }

  private OffsetDateTime now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
  }

  private static UUID parseId(String value) {
    if (value.length() != ID_PREFIX.length() + 36 || !value.startsWith(ID_PREFIX)) throw notFound();
    try {
      return UUID.fromString(value.substring(ID_PREFIX.length()));
    } catch (IllegalArgumentException ex) {
      throw notFound();
    }
  }

  private static BusinessException unavailable() {
    return new BusinessException(ErrorCode.SCREENING_UNAVAILABLE);
  }

  private static BusinessException notFound() {
    return new BusinessException(ErrorCode.NOT_FOUND);
  }
}
