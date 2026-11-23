package com.chozy.server.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.analysis.service.ManualAnalysisWorker;
import com.chozy.server.report.repository.ReportRepository;
import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ReportDataFixture;
import com.chozy.server.support.ReportInputRefreshFixture;
import com.chozy.server.support.UrlAnalysisFixture;
import com.chozy.server.usage.service.ReportUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

@Import({ReportInputRefreshFixture.class, UrlAnalysisFixture.class})
@TestPropertySource(properties = "app.analysis.worker-cron=-")
class ReportInputsDatabaseIT extends AccountPostgresITSupport {
  private static final String REPORT = "/search/reports/{id}";
  private static final String INPUTS = REPORT + "/inputs";
  @Autowired ManualAnalysisWorker worker;
  @Autowired ReportUsageService usage;
  @Autowired ReportRepository reports;

  @Test
  void appliesCpcBoundariesAndClearingWithNoScoreChangeOrUsage() throws Exception {
    var owner = account();
    long id = report(owner, """
        {"summary":{"score":80,"investmentGrade":"SUITABLE"},
         "internalDiagnosis":{"profitability":{"referenceSellingPrice":10000,"feeRate":0,"profitPerUnit":2000}},
         "externalDiagnosis":{"competitionLevel":"HIGH"},
         "investmentSimulation":{"warnings":["기존 안내"]}}
        """);
    var stale = reports.findOwned(id, owner.id()).orElseThrow();
    update(owner, id, "{\"cpc\":600}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.investmentGrade").value("SUITABLE"));
    update(owner, id, "{\"cpc\":1000}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.investmentGrade").value("CONDITIONAL"));
    update(owner, id, "{\"cpc\":1000.01}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.investmentGrade").value("NOT_RECOMMENDED"))
        .andExpect(jsonPath("$.data.summary.score").value(80));
    assertThat(reports.updateSnapshot(stale, "{}")).isFalse();
    update(owner, id, "{\"cpc\":0}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.investmentGrade").value("SUITABLE"));
    update(owner, id, "{\"cpc\":null}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedInputs.cpc").value(nullValue()))
        .andExpect(jsonPath("$.data.summary.investmentGrade").value("CONDITIONAL"));
    read(owner, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.investmentSimulation.cpc").value(nullValue()))
        .andExpect(jsonPath("$.data.investmentSimulation.warnings.length()").value(1))
        .andExpect(jsonPath("$.data.summary.score").value(80));
    assertThat(usage.current(owner.id()).usedCount()).isZero();
  }

  @Test
  void appliesPriceAndCategoryIndependentlyAndStoresRefreshedKeyword() throws Exception {
    var owner = account();
    long id = report(owner, "{\"summary\":{\"scoreStatus\":\"WAITING_FOR_INPUT\"}}");
    update(owner, id, "{\"expectedSellingPrice\":16000}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.requiredInputs[0]").value("COUPANG_CATEGORY"))
        .andExpect(jsonPath("$.data.summary.score").value(nullValue()));
    update(owner, id, "{\"coupangCategoryCode\":\"ELECTRONICS\"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedInputs.expectedSellingPrice").value(16000))
        .andExpect(jsonPath("$.data.summary.score").value(100))
        .andExpect(jsonPath("$.data.summary.requiredInputs").isEmpty());
    update(owner, id, "{\"keyword\":\" new keyword \"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedInputs.keyword").value("new keyword"))
        .andExpect(jsonPath("$.data.updatedInputs.coupangCategoryCode").value("ELECTRONICS"))
        .andExpect(jsonPath("$.data.summary.score").value(88));
    read(owner, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.externalDiagnosis.monthlySearchCount").value(200))
        .andExpect(jsonPath("$.data.investmentStrategy.coupangRegistration.categorySource").value("USER_SELECTED"))
        .andExpect(jsonPath("$.data.internalDiagnosis.profitability.feeRate").value(8.58))
        .andExpect(jsonPath("$.data.internalDiagnosis.profitability.profitPerUnit").value(6627.2));
    assertThat(usage.current(owner.id()).usedCount()).isZero();
  }

  @Test
  void validatesAndAppliesAnOptionFromTheReportsSupplierProduct() throws Exception {
    var owner = account();
    var accepted = mvc.perform(post("/search/analyses/url").header("Authorization", owner.bearer())
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"url":"https://domeme.domeggook.com/12345678","selectedOptionId":"RED"}
            """)).andExpect(status().isAccepted());
    long analysis = response(accepted).at("/data/analysisId").longValue();
    worker.runNext();
    long id = response(mvc.perform(get("/search/analyses/{id}/status", analysis).header("Authorization", owner.bearer()))
        .andExpect(status().isOk())).at("/data/reportId").longValue();
    update(owner, id, "{\"selectedOptionId\":\"SMALL\"}").andExpect(status().isBadRequest());
    update(owner, id, "{\"selectedOptionId\":\"BLUE\",\"coupangCategoryCode\":\"ELECTRONICS\"}")
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedInputs.selectedOptionId").value("BLUE"));
    read(owner, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.product.selectedOption.price").value(8500))
        .andExpect(jsonPath("$.data.internalDiagnosis.profitability.wholesalePrice").value(8500))
        .andExpect(jsonPath("$.data.internalDiagnosis.profitability.referenceSellingPrice").value(19000));
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
  }

  @Test
  void rejectsOtherOwnersAndMalformedOrUnsupportedInputs() throws Exception {
    var owner = account();
    var other = account();
    long id = report(owner, "{}");
    mvc.perform(patch(INPUTS, id).contentType(MediaType.APPLICATION_JSON).content("{\"cpc\":0}"))
        .andExpect(status().isUnauthorized());
    update(other, id, "{\"cpc\":0}").andExpect(status().isNotFound());
    update(owner, id, "{}").andExpect(status().isBadRequest());
    update(owner, id, "{\"expectedSellingPrice\":99}").andExpect(status().isBadRequest());
    update(owner, id, "{\"expectedSellingPrice\":\"5000\"}").andExpect(status().isBadRequest());
    update(owner, id, "{\"cpc\":-1}").andExpect(status().isBadRequest());
    update(owner, id, "{\"keyword\":null}").andExpect(status().isBadRequest());
    update(owner, id, "{\"coupangCategoryCode\":\"UNKNOWN\"}").andExpect(status().isBadRequest());
    update(owner, id, "{\"selectedOptionId\":\"RED\"}").andExpect(status().isBadRequest());
    assertThat(reports.findOwned(id, owner.id()).orElseThrow().snapshot()).isEqualTo("{}");
  }

  private long report(Account owner, String snapshot) {
    var data = new ReportDataFixture(jdbc);
    return data.report(data.analysis(owner.id(), "Lantern", "COMPLETED", "2026-11-23T01:01:00Z"), snapshot);
  }

  private ResultActions update(Account owner, long id, String input) throws Exception {
    return mvc.perform(patch(INPUTS, id).header("Authorization", owner.bearer())
        .contentType(MediaType.APPLICATION_JSON).content(input));
  }

  private ResultActions read(Account owner, long id) throws Exception {
    return mvc.perform(get(REPORT, id).header("Authorization", owner.bearer()));
  }
}
