package com.chozy.server.favorite.service;

import static com.chozy.server.analysis.dto.AnalysisSource.MANUAL;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.response.Pagination;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.favorite.dto.Favorites.*;
import com.chozy.server.favorite.repository.FavoriteRepository;
import com.chozy.server.favorite.repository.FavoriteRepository.Entry;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.report.service.ReportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class FavoriteService {
  public static final int LIMIT = 10;
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final String MANUAL_SUPPLIER = "공급사 직접 입력(API 미지원 사이트)";
  private static final Comparator<BigDecimal> SCORE_DESC = Comparator.nullsLast(Comparator.reverseOrder());

  private final MemberAccess members;
  private final ReportService reports;
  private final FavoriteRepository favorites;
  private final Clock clock;

  public FavoriteService(MemberAccess members, ReportService reports, FavoriteRepository favorites, Clock clock) {
    this.members = members;
    this.reports = reports;
    this.favorites = favorites;
    this.clock = clock;
  }

  @Transactional
  public RegistrationResult register(AuthenticatedMember member, Register request) {
    long memberId = members.lockCurrent(member).getId();
    var report = reports.detail(member, request.reportId());
    if (report.source() != MANUAL) throw new BusinessException(ErrorCode.INVALID_INPUT);
    var existing = favorites.findByReport(memberId, report.reportId());
    var counts = counts(memberId);
    if (existing.isPresent()) {
      var entry = existing.get();
      transition(memberId, entry, request.status());
      return new RegistrationResult(false, registration(entry, request.status(), counts.total()));
    }
    if (counts.total() >= LIMIT) throw new BusinessException(ErrorCode.FAVORITE_LIMIT_REACHED);

    var now = now();
    long id = favorites.insert(memberId, report, request.status(), now);
    return new RegistrationResult(true, registration(owned(memberId, id), request.status(), counts.total() + 1));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Listing list(AuthenticatedMember member, Status status, Sort sort, int page, int size) {
    long memberId = members.requireCurrent(member).getId();
    var counts = counts(memberId);
    var entries = favorites.list(memberId, status);
    // The free plan bounds this list to ten items. Stable sorting keeps newest first on ties.
    var stream = switch (sort) {
      case SCORE -> entries.stream().sorted(Comparator.comparing(Entry::score, SCORE_DESC));
      case CHANGE -> entries.stream().sorted(Comparator.comparing(FavoriteService::scoreChange, SCORE_DESC));
      case LATEST -> entries.stream();
    };
    var items = stream.skip((long) page * size).limit(size).map(FavoriteService::item).toList();
    return new Listing(new LimitedCounts(counts, LIMIT), items, Pagination.of(page, page, size, entries.size()));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Detail detail(AuthenticatedMember member, long favoriteId, int periodDays) {
    if (periodDays != 7 && periodDays != 14 && periodDays != 30 && periodDays != 90) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    var entry = owned(members.requireCurrent(member).getId(), favoriteId);
    var product = new Product(MANUAL, null, entry.productName(), null, null, null, entry.wholesalePrice(),
        entry.expectedSellingPrice(), entry.shippingFee(), entry.profitPerUnit(), entry.marginRate(), null, null);
    var score = new ScoreHistory(entry.score(), entry.score(), scoreChange(entry), entry.supplyScore(),
        entry.supplyScore(), entry.marketScore(), entry.marketScore(), entry.createdAt(), null, null, null,
        MarketPriceRefreshStatus.UNAVAILABLE);
    return new Detail(entry.id(), entry.reportId(), entry.status(), product, null, null, score, null,
        favorites.latestSale(entry.id()).orElse(null), new PurchaseLink(null, false), entry.createdAt(),
        entry.updatedAt());
  }

  @Transactional
  public StatusChange changeStatus(AuthenticatedMember member, long favoriteId, Status status) {
    long memberId = members.lockCurrent(member).getId();
    var entry = owned(memberId, favoriteId);
    var updatedAt = transition(memberId, entry, status);
    return new StatusChange(entry.id(), entry.status(), status, counts(memberId), updatedAt);
  }

  @Transactional
  public SalesResult recordSale(AuthenticatedMember member, long favoriteId, SaleResult result) {
    long memberId = members.lockCurrent(member).getId();
    var entry = owned(memberId, favoriteId);
    var now = now();
    long id = favorites.recordSale(entry, result, now);
    return new SalesResult(id, entry.id(), entry.reportId(), MANUAL, null, result, entry.score(),
        entry.supplyScore(), entry.marketScore(), now);
  }

  @Transactional
  public void delete(AuthenticatedMember member, long favoriteId) {
    long memberId = members.lockCurrent(member).getId();
    if (!favorites.delete(memberId, favoriteId, now())) throw new BusinessException(ErrorCode.NOT_FOUND);
  }

  public Counts counts(long memberId) {
    return favorites.counts(memberId);
  }

  private Entry owned(long memberId, long favoriteId) {
    return favorites.find(memberId, favoriteId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private OffsetDateTime transition(long memberId, Entry entry, Status requested) {
    if (entry.status() == requested) return entry.statusUpdatedAt();
    var now = now();
    favorites.changeStatus(memberId, entry.id(), requested, now);
    return now;
  }

  private OffsetDateTime now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
  }

  private static Registration registration(Entry entry, Status status, long count) {
    return new Registration(entry.id(), entry.reportId(), MANUAL, null, entry.productName(), status, null,
        entry.score(), entry.wholesalePrice(), entry.createdAt(), count, LIMIT);
  }

  private static Item item(Entry entry) {
    return new Item(entry.id(), entry.reportId(), MANUAL, null, entry.productName(), null, MANUAL_SUPPLIER,
        entry.status(), null, null, entry.wholesalePrice(), null, entry.score(), scoreChange(entry),
        entry.profitPerUnit(), entry.marginRate(), entry.createdAt(), entry.updatedAt());
  }

  private static BigDecimal scoreChange(Entry entry) {
    // There is no independent market refresh for manual reports yet.
    return entry.score() == null ? null : BigDecimal.ZERO;
  }

  public record RegistrationResult(boolean created, Registration data) {}
}
