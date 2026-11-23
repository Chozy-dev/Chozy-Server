package com.chozy.server.report.service;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.UrlAnalyses.Option;
import com.chozy.server.analysis.service.UrlProductCatalog;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.report.dto.CoupangCategories.CoupangCategory;
import com.chozy.server.report.dto.ReportDetail;
import com.chozy.server.report.dto.ReportInputs;
import com.chozy.server.report.dto.ReportInputs.RecalculatedSummary;
import com.chozy.server.report.dto.ReportInputs.ReportInputPatch;
import com.chozy.server.report.dto.ReportInputs.UpdatedInputs;
import com.chozy.server.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile({"database", "dbdev"})
public class ReportInputService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final ReportRepository reports;
  private final MemberAccess members;
  private final ObjectProvider<UrlProductCatalog> catalogs;
  private final ObjectProvider<ReportInputDataProvider> providers;
  private final ReportSnapshotReader snapshots;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public ReportInputService(ReportRepository reports, MemberAccess members, ObjectProvider<UrlProductCatalog> catalogs,
      ObjectProvider<ReportInputDataProvider> providers, ObjectMapper mapper, PlatformTransactionManager manager,
      Clock clock) {
    this.reports = reports;
    this.members = members;
    this.catalogs = catalogs;
    this.providers = providers;
    this.snapshots = new ReportSnapshotReader(mapper);
    this.transactions = new TransactionTemplate(manager);
    this.clock = clock;
  }

  public ReportInputs update(AuthenticatedMember member, long reportId, ReportInputPatch request) {
    if (!request.hasInputs()) throw invalid();
    members.requireCurrent(member);
    var stored = reports.findOwned(reportId, member.memberId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    var draft = snapshots.tree(stored);
    var current = snapshots.read(stored, draft);
    var previous = inputs(current);
    var category = category(request.getCoupangCategoryCode());
    boolean userSelectedCategory =
        "USER_SELECTED".equals(draft.at("/investmentStrategy/coupangRegistration/categorySource").asText());
    boolean categoryChanged = category != null && (!category.categoryCode().equals(previous.coupangCategoryCode())
        || !userSelectedCategory
        || differs(category.feeRate(), ReportRecalculator.decimal(draft.at("/internalDiagnosis/profitability/feeRate"))));
    var requestedPrice = request.getExpectedSellingPrice();
    boolean priceChanged = requestedPrice != null && differs(requestedPrice, previous.expectedSellingPrice());
    String keyword = request.getKeyword();
    boolean keywordChanged = keyword != null && !keyword.equals(previous.keyword());
    String optionId = request.getSelectedOptionId();
    if (optionId != null && current.source() == AnalysisSource.MANUAL) throw invalid();
    boolean optionChanged = optionId != null && !optionId.equals(previous.selectedOptionId());
    var cpc = request.hasCpc() ? request.getCpc() : previous.cpc();
    boolean cpcChanged = differs(cpc, previous.cpc());

    if (!categoryChanged && !priceChanged && !keywordChanged && !optionChanged && !cpcChanged) {
      String updatedAt = draft.path("inputsUpdatedAt").asText(null);
      return result(current, updatedAt == null ? current.analyzedAt() : OffsetDateTime.parse(updatedAt));
    }

    boolean refreshed = keywordChanged || optionChanged;
    var originalMarket = draft.path("externalDiagnosis").deepCopy();
    if (refreshed) {
      var option = optionChanged ? option(current, optionId) : null;
      var provider = providers.getIfAvailable();
      if (provider == null) throw unavailable();
      provider.refresh(current, draft, new ReportInputDataProvider.Changes(keywordChanged ? keyword : null, option));
      if (keywordChanged) draft.withObjectProperty("externalDiagnosis").put("keyword", keyword);
      if (optionChanged) {
        draft.withObjectProperty("product").putObject("selectedOption")
            .put("optionId", option.optionId()).put("optionName", option.optionName())
            .put("price", option.price()).put("stockQuantity", option.stockQuantity());
        draft.withObjectProperty("internalDiagnosis").withObjectProperty("profitability")
            .put("wholesalePrice", option.price());
      }
    }

    var price = priceChanged ? requestedPrice : previous.expectedSellingPrice();
    boolean recalculate = priceChanged || categoryChanged || refreshed;
    var categoryToApply = categoryChanged ? category
        : refreshed && userSelectedCategory ? category(previous.coupangCategoryCode()) : null;
    boolean marketChanged = keywordChanged || !originalMarket.equals(draft.path("externalDiagnosis"));
    ReportRecalculator.apply(draft, stored, price, categoryToApply, cpc, recalculate, marketChanged);
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS).atZone(KOREA).toOffsetDateTime();
    draft.put("inputsUpdatedAt", now.toString());
    var response = result(snapshots.read(stored, draft), now);

    transactions.executeWithoutResult(status -> {
      members.lockCurrent(member);
      if (!reports.updateSnapshot(stored, draft.toString())) {
        throw new BusinessException(ErrorCode.REPORT_INPUTS_CONFLICT);
      }
    });
    return response;
  }

  private static UpdatedInputs inputs(ReportDetail report) {
    var internal = report.internalDiagnosis();
    var profitability = internal == null ? null : internal.profitability();
    var category = report.investmentStrategy().coupangRegistration();
    var product = report.product();
    var option = product == null ? null : product.selectedOption();
    var simulation = report.investmentSimulation();
    return new UpdatedInputs(profitability == null ? null : profitability.expectedSellingPrice(),
        category.categoryCode(), report.externalDiagnosis().keyword(), option == null ? null : option.optionId(),
        simulation == null ? null : simulation.cpc());
  }

  private static ReportInputs result(ReportDetail report, OffsetDateTime updatedAt) {
    return new ReportInputs(report.reportId(), inputs(report), RecalculatedSummary.from(report.summary()), updatedAt);
  }

  private static CoupangCategory category(String code) {
    if (code == null) return null;
    return CoupangCategoryCatalog.list().categories().stream().filter(category -> category.categoryCode().equals(code))
        .findFirst().orElseThrow(ReportInputService::invalid);
  }

  private Option option(ReportDetail report, String optionId) {
    if (report.product() == null || report.product().productId() == null) throw invalid();
    var catalog = catalogs.getIfAvailable();
    if (catalog == null) throw unavailable();
    try {
      return catalog.find(report.source(), report.product().productId()).stream()
          .flatMap(product -> product.options().stream()).filter(option -> option.optionId().equals(optionId))
          .findFirst().orElseThrow(ReportInputService::invalid);
    } catch (BusinessException ex) {
      if (ex.errorCode() == ErrorCode.URL_ANALYSIS_UNAVAILABLE) throw unavailable();
      throw ex;
    }
  }

  private static boolean differs(BigDecimal left, BigDecimal right) {
    return left == null || right == null ? !Objects.equals(left, right) : left.compareTo(right) != 0;
  }

  private static BusinessException invalid() {
    return new BusinessException(ErrorCode.INVALID_INPUT);
  }

  private static BusinessException unavailable() {
    return new BusinessException(ErrorCode.REPORT_RECALCULATION_UNAVAILABLE);
  }
}
