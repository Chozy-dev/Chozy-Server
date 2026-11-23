package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.AnalysisStatus;
import com.chozy.server.analysis.dto.AnalysisType;
import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.chozy.server.analysis.dto.ManualAnalysisResponses.Accepted;
import com.chozy.server.analysis.dto.ManualAnalysisResponses.Progress;
import com.chozy.server.analysis.dto.UrlAnalyses.UrlAccepted;
import com.chozy.server.analysis.dto.UrlAnalyses.UrlInput;
import com.chozy.server.analysis.repository.ManualAnalysisRepository;
import com.chozy.server.analysis.repository.ManualAnalysisRepository.Job;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.usage.service.ReportUsageService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class ManualAnalysisService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final int TOTAL_STEPS = 7;
  private static final String SOURCE = "MANUAL";

  private final ManualAnalysisRepository jobs;
  private final MemberAccess members;
  private final ReportUsageService usage;
  private final ObjectProvider<ManualAnalysisEngine> engine;
  private final ObjectProvider<UrlAnalysisEngine> urlEngine;
  private final Clock clock;

  public ManualAnalysisService(ManualAnalysisRepository jobs, MemberAccess members, ReportUsageService usage,
      ObjectProvider<ManualAnalysisEngine> engine, ObjectProvider<UrlAnalysisEngine> urlEngine, Clock clock) {
    this.jobs = jobs;
    this.members = members;
    this.usage = usage;
    this.engine = engine;
    this.urlEngine = urlEngine;
    this.clock = clock;
  }

  @Transactional
  public Accepted submit(AuthenticatedMember member, ManualAnalysisInput request) {
    if (request.hasPricePolicy() && request.minimumSellingPrice() == null)
      throw new BusinessException(ErrorCode.INVALID_INPUT);

    long memberId = reserveSlot(member);
    if (engine.getIfAvailable() == null)
      throw new BusinessException(ErrorCode.ANALYSIS_UNAVAILABLE);

    String keyword = request.keyword() == null || request.keyword().isBlank() ? null : request.keyword().strip();
    var minimumPrice = request.hasPricePolicy() ? request.minimumSellingPrice() : null;
    var input = new ManualAnalysisInput(request.productName().strip(), request.wholesalePrice(),
        request.shippingFee(), request.hasPricePolicy(), minimumPrice, keyword);
    var now = now();
    long id = jobs.enqueue(memberId, input, now);
    return new Accepted(id, null, SOURCE, input.productName(), input.keyword(), AnalysisStatus.PENDING, now,
        List.of("PROFITABILITY", "MARKET_TREND", "COMPETITION", "SALES_STRATEGY", "AD_SIMULATION"),
        List.of("SUPPLY_STABILITY", "SUPPLIER_RELIABILITY"));
  }

  @Transactional
  public UrlAccepted submitUrl(AuthenticatedMember member, UrlInput input) {
    long memberId = reserveSlot(member);
    if (urlEngine.getIfAvailable() == null)
      throw new BusinessException(ErrorCode.URL_ANALYSIS_UNAVAILABLE);
    var now = now();
    long id = jobs.enqueueUrl(memberId, input, now);
    return new UrlAccepted(id, null, input.source(), input.productId(), input.productName(), input.keyword(),
        input.selectedOptionId(), AnalysisStatus.PENDING, now);
  }

  private long reserveSlot(AuthenticatedMember member) {
    members.lockCurrent(member);
    long memberId = member.memberId();
    long remaining = usage.current(memberId).remainingCount();
    if (remaining == 0)
      throw new BusinessException(ErrorCode.REPORT_LIMIT_EXHAUSTED);
    if (jobs.activeCount(memberId) >= remaining)
      throw new BusinessException(ErrorCode.REPORT_SLOTS_RESERVED);
    return memberId;
  }

  @Transactional(readOnly = true)
  public Progress progress(AuthenticatedMember member, long id) {
    members.requireCurrent(member);
    var job = jobs.findOwned(id, member.memberId()).orElseThrow(ManualAnalysisService::notFound);
    int step = switch (job.status()) {
      case PENDING -> 0;
      case PROCESSING, FAILED -> 1;
      case COMPLETED -> TOTAL_STEPS;
    };
    var completedAt = job.completedAt() == null ? null : job.completedAt().atZoneSameInstant(KOREA).toOffsetDateTime();
    return new Progress(id, job.status(), step, TOTAL_STEPS, job.reportId(), completedAt, job.errorMessage());
  }

  @Transactional
  public Optional<Job> claim() {
    var now = now();
    jobs.expire(now, "정밀분석 처리 시간이 초과되었습니다. 다시 시도해 주세요.");
    return jobs.claim(now.plusMinutes(5), engine.getIfAvailable() != null, urlEngine.getIfAvailable() != null);
  }

  @Transactional
  public void complete(Job job, ManualAnalysisEngine.Result result) {
    var snapshot = result.snapshot().deepCopy();
    if (snapshot.isEmpty()) throw new IllegalArgumentException("Empty report");
    members.lock(job.memberId());
    var now = now();
    if (!jobs.complete(job.id(), now)) return;
    var url = job.urlInput();
    snapshot.put("analysisId", job.id()).put("analyzedAt", now.toString());
    if (url == null) {
      snapshot.put("analysisType", AnalysisType.MANUAL.name()).put("source", SOURCE);
    } else {
      snapshot.put("analysisType", AnalysisType.URL.name()).put("source", url.source().name());
      var product = snapshot.withObjectProperty("product");
      product.put("productId", url.productId()).put("originalUrl", url.originalUrl()).put("resolvedUrl", url.resolvedUrl());
      if (url.productName() != null) product.put("productName", url.productName());
      if (url.selectedOptionId() == null) product.putNull("selectedOption");
      else product.withObjectProperty("selectedOption").put("optionId", url.selectedOptionId());
    }
    long reportId = jobs.saveReport(job.id(), snapshot.toString());
    usage.recordCreatedReport(job.memberId(), reportId, result.dataLabQuotaExhausted(), now.toInstant());
  }

  @Transactional
  public void fail(Job job, String message) {
    jobs.fail(job.id(), now(), message);
  }

  private OffsetDateTime now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
  }

  private static BusinessException notFound() {
    return new BusinessException(ErrorCode.NOT_FOUND);
  }
}
