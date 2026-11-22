package com.chozy.server.report.service;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.report.dto.ReportDetail;
import com.chozy.server.report.dto.ReportFeedback;
import com.chozy.server.report.dto.ReportFeedback.Opinion;
import com.chozy.server.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class ReportService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final ReportRepository reports;
  private final MemberAccess members;
  private final ReportSnapshotReader snapshots;
  private final Clock clock;

  public ReportService(ReportRepository reports, MemberAccess members, ObjectMapper mapper, Clock clock) {
    this.reports = reports;
    this.members = members;
    this.snapshots = new ReportSnapshotReader(mapper);
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ReportDetail detail(AuthenticatedMember member, long reportId) {
    members.requireCurrent(member);
    return ownedReport(member.memberId(), reportId);
  }

  @Transactional
  public ReportFeedback saveFeedback(AuthenticatedMember member, long reportId, Opinion opinion) {
    members.requireCurrent(member);
    var report = ownedReport(member.memberId(), reportId);
    var summary = report.summary();
    if (summary.score() == null) throw new BusinessException(ErrorCode.REPORT_SCORE_UNAVAILABLE);

    var now = clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
    String productId = report.product().productId();
    long id = reports.insertFeedback(reportId, productId, summary, opinion, now)
        .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE));
    return new ReportFeedback(id, reportId, productId, summary.score(), summary.supplyConditionScore(),
        summary.marketConditionScore(), opinion, now);
  }

  private ReportDetail ownedReport(long memberId, long reportId) {
    return reports.findOwned(reportId, memberId).map(snapshots::read)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }
}
