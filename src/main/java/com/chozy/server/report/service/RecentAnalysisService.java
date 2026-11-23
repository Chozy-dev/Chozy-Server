package com.chozy.server.report.service;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.AnalysisType;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.report.dto.RecentAnalyses;
import com.chozy.server.report.dto.RecentAnalyses.RecentAnalysisItem;
import com.chozy.server.report.dto.ReportSummary;
import com.chozy.server.report.repository.RecentAnalysisRepository;
import com.chozy.server.report.repository.RecentAnalysisRepository.Entry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class RecentAnalysisService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final RecentAnalysisRepository history;
  private final MemberAccess members;
  private final ObjectReader summaries;

  public RecentAnalysisService(RecentAnalysisRepository history, MemberAccess members, ObjectMapper mapper) {
    this.history = history;
    this.members = members;
    this.summaries = mapper.readerFor(ReportSummary.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RecentAnalyses recent(AuthenticatedMember member, int page, int size) {
    members.requireCurrent(member);
    long total = history.countOwned(member.memberId());
    long offset = (long) page * size;
    long pages = Math.ceilDiv(total, size);
    var items = offset >= total ? List.<RecentAnalysisItem>of() : history.findOwned(member.memberId(), offset, size)
        .stream().map(this::item).toList();
    return new RecentAnalyses(items, page, size, total, pages, page + 1L < pages);
  }

  private RecentAnalysisItem item(Entry entry) {
    ReportSummary summary = summary(entry.summary());
    var analyzedAt = entry.analyzedAt() == null ? null : entry.analyzedAt().atZoneSameInstant(KOREA).toOffsetDateTime();
    var type = entry.source() == AnalysisSource.MANUAL ? AnalysisType.MANUAL : AnalysisType.URL;
    return new RecentAnalysisItem(entry.analysisId(), entry.reportId(), type, entry.source(),
        entry.productId(), entry.productName(), entry.imageUrl(), entry.keyword(), summary == null ? null : summary.score(),
        summary == null ? null : summary.investmentGrade(), entry.status(), analyzedAt);
  }

  private ReportSummary summary(String json) {
    if (json == null) return null;
    try {
      return summaries.readValue(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Invalid stored report summary", ex);
    }
  }
}
