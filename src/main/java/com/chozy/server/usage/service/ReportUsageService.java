package com.chozy.server.usage.service;

import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.usage.config.ReportUsageProperties;
import com.chozy.server.usage.repository.ReportUsageRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
@EnableConfigurationProperties(ReportUsageProperties.class)
public class ReportUsageService {
  private final ReportUsageRepository repository;
  private final ReportUsageProperties properties;
  private final MemberAccess members;
  private final Clock clock;

  public ReportUsageService(ReportUsageRepository repository, ReportUsageProperties properties,
      MemberAccess members, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.members = members;
    this.clock = clock;
  }

  public ReportUsage current(long memberId) {
    return forMonth(memberId, UsageMonth.at(clock.instant()));
  }

  public ReportUsage forMonth(long memberId, UsageMonth month) {
    long usedCount = repository.countCharged(memberId, month.startDate());
    int limit = properties.freeMonthlyReportLimit();
    return new ReportUsage(month.period().toString(), "MONTH", usedCount, limit,
        Math.max(0, limit - usedCount), month.nextResetAt());
  }

  /**
   * Call in the same transaction as creation of a new report, with its stable persisted ID.
   * Roll back report creation if quota is exhausted. The caller supplies DataLab quota status
   * from the trusted integration. Reads, failed analyses and report edits do not call this method.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreatedReport(long memberId, long reportId, boolean dataLabQuotaExhausted) {
    members.lock(memberId);
    var existingOwner = repository.findReportOwner(reportId);
    if (existingOwner.isPresent()) {
      if (existingOwner.get() != memberId) throw new IllegalArgumentException("Report owner mismatch");
      return;
    }

    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    var month = UsageMonth.at(now);
    if (!dataLabQuotaExhausted && forMonth(memberId, month).remainingCount() == 0)
      throw new ReportQuotaExceededException();
    repository.insert(memberId, reportId, month.startDate(),
        now.atZone(UsageMonth.KOREA).toOffsetDateTime(), dataLabQuotaExhausted);
  }
}
