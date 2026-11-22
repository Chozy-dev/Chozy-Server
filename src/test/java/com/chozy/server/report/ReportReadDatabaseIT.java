package com.chozy.server.report;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ReportDataFixture;
import org.junit.jupiter.api.Test;

class ReportReadDatabaseIT extends AccountPostgresITSupport {
  private static final String REPORT = "/search/reports/{id}";
  private static final String RECENT = "/search/analyses/recent";
  private static final String FINISHED = "2026-11-23T01:01:00Z";

  @Test
  void readsAnExistingV6ReportThroughTheNewContract() throws Exception {
    var owner = auth.login(new Login(V2_LOGIN, PASSWORD));
    String bearer = "Bearer " + owner.accessToken();
    mvc.perform(get(REPORT, V6_REPORT_ID).header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reportId").value(V6_REPORT_ID))
        .andExpect(jsonPath("$.data.analysisType").value("MANUAL"))
        .andExpect(jsonPath("$.data.analyzedAt").value("2026-01-14T12:01:00+09:00"))
        .andExpect(jsonPath("$.data.product.productName").value("Legacy lantern"))
        .andExpect(jsonPath("$.data.product.purchaseUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.product.isAffiliateLink").value(false))
        .andExpect(jsonPath("$.data.product.supplyConditions").value(nullValue()))
        .andExpect(jsonPath("$.data.summary.scoreStatus").value("INSUFFICIENT_DATA"))
        .andExpect(jsonPath("$.data.summary.score").value(nullValue()))
        .andExpect(jsonPath("$.data.summary.requiredInputs").isEmpty())
        .andExpect(jsonPath("$.data.internalDiagnosis.supplyStability").value(nullValue()))
        .andExpect(jsonPath("$.data.internalDiagnosis.supplierReliability").value(nullValue()))
        .andExpect(jsonPath("$.data.internalDiagnosis.profitability.minimumSellingPrice").value(11000))
        .andExpect(jsonPath("$.data.externalDiagnosis.monthlySearchCount").value(nullValue()))
        .andExpect(jsonPath("$.data.externalDiagnosis.searchTrend").value(nullValue()))
        .andExpect(jsonPath("$.data.externalDiagnosis.seasonality.peakMonths").isArray())
        .andExpect(jsonPath("$.data.investmentStrategy.channelEfficiency.bidEstimates").isArray())
        .andExpect(jsonPath("$.data.investmentSimulation.sellerCosts.packagingCost").value(0))
        .andExpect(jsonPath("$.data.unavailableFeatures")
            .value(containsInAnyOrder("SUPPLY_STABILITY", "SUPPLIER_RELIABILITY")));
    mvc.perform(get(RECENT).header("Authorization", bearer)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.analyses[0].reportId").value(V6_REPORT_ID))
        .andExpect(jsonPath("$.data.analyses[0].score").value(nullValue()));
  }

  @Test
  void pagesByActivityAndIdAndKeepsUnfinishedRowsAndOwnersSeparate() throws Exception {
    var owner = account();
    var other = account();
    var data = new ReportDataFixture(jdbc);
    long completed = data.analysis(owner.id(), "Completed", "COMPLETED", FINISHED);
    long report = data.report(completed, """
        {"summary":{"score":72.5,"investmentGrade":"CONDITIONAL"},
         "externalDiagnosis":{"keyword":"actual keyword","searchTrend":{"last30Days":[{"date":"2026-11-22","value":56.1}]}},
         "investmentSimulation":{"cpc":123.45},"internalOnly":true}
        """);
    long failed = data.analysis(owner.id(), "Failed", "FAILED", FINISHED);
    data.analysis(other.id(), "Other", "FAILED", "2026-11-23T01:02:00Z");
    long pending = data.analysis(owner.id(), "Pending", "PENDING", null);
    try {
      mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("size", "1"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3))
          .andExpect(jsonPath("$.data.totalPages").value(3)).andExpect(jsonPath("$.data.hasNext").value(true))
          .andExpect(jsonPath("$.data.analyses[0].analysisId").value(failed))
          .andExpect(jsonPath("$.data.analyses[0].analysisStatus").value("FAILED"))
          .andExpect(jsonPath("$.data.analyses[0].reportId").value(nullValue()))
          .andExpect(jsonPath("$.data.analyses[0].analyzedAt").value(nullValue()));
      mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("page", "1").param("size", "1"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.analyses[0].analysisId").value(completed))
          .andExpect(jsonPath("$.data.analyses[0].reportId").value(report))
          .andExpect(jsonPath("$.data.analyses[0].keyword").value("actual keyword"))
          .andExpect(jsonPath("$.data.analyses[0].score").value(72.5))
          .andExpect(jsonPath("$.data.analyses[0].investmentGrade").value("CONDITIONAL"))
          .andExpect(jsonPath("$.data.analyses[0].analyzedAt").value("2026-11-23T10:01:00+09:00"));
      mvc.perform(get(RECENT).header("Authorization", owner.bearer()))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(10))
          .andExpect(jsonPath("$.data.analyses[2].analysisId").value(pending))
          .andExpect(jsonPath("$.data.analyses[2].analysisStatus").value("PENDING"))
          .andExpect(jsonPath("$.data.analyses[2].score").value(nullValue()))
          .andExpect(jsonPath("$.data.hasNext").value(false));
      mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("page", "2147483647"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.analyses").isEmpty())
          .andExpect(jsonPath("$.data.totalElements").value(3));
    } finally {
      jdbc.sql("DELETE FROM chozy.manual_analysis_jobs WHERE id = :id")
          .param("id", pending).update();
    }

    mvc.perform(get(REPORT, report).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.score").value(72.5))
        .andExpect(jsonPath("$.data.externalDiagnosis.searchTrend.last30Days[0].value").value(56.1))
        .andExpect(jsonPath("$.data.externalDiagnosis.searchTrend.last12Months").isEmpty())
        .andExpect(jsonPath("$.data.investmentSimulation.cpc").value(123.45))
        .andExpect(jsonPath("$.data.internalOnly").doesNotExist());
    mvc.perform(get(REPORT, report).header("Authorization", other.bearer()))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C003"));
  }

  @Test
  void rejectsInvalidPaginationAndRequiresAuthentication() throws Exception {
    mvc.perform(get(RECENT)).andExpect(status().isUnauthorized());
    mvc.perform(get(REPORT, V6_REPORT_ID)).andExpect(status().isUnauthorized());
    var owner = account();
    mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("page", "-1"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("size", "101"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    mvc.perform(get(RECENT).header("Authorization", owner.bearer()).param("size", "foo"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C005"));
    mvc.perform(get(RECENT).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalPages").value(0))
        .andExpect(jsonPath("$.data.analyses").isEmpty());
  }
}
