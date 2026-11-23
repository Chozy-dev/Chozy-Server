package com.chozy.server.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.analysis.service.ManualAnalysisWorker;
import com.chozy.server.support.AccountPostgresITSupport;
import com.chozy.server.support.ManualAnalysisFixture;
import com.chozy.server.support.UrlAnalysisFixture;
import com.chozy.server.usage.service.ReportUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

@Import({UrlAnalysisFixture.class, ManualAnalysisFixture.class})
@TestPropertySource(properties = {"app.analysis.worker-cron=-", "app.usage.free-monthly-report-limit=1"})
class UrlAnalysisDatabaseIT extends AccountPostgresITSupport {
  private static final String RESOLVE = "/search/products/resolve-url";
  private static final String SUBMIT = "/search/analyses/url";
  private static final String STATUS = "/search/analyses/{id}/status";
  private static final String DOMEGGOOK = "https://domeggook.com/12345678";
  private static final String DOMEME = "https://domeme.domeggook.com/12345678";
  private static final String OWNERCLAN = "https://www.ownerclan.com/V2/product/view.php?selfcode=Wabc123";

  @Autowired ManualAnalysisWorker worker;
  @Autowired ReportUsageService usage;

  @Test
  void resolvesConversionAbsenceAndEqualPricesWithoutAuthentication() throws Exception {
    resolve(DOMEGGOOK).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("DOMEGGOOK"))
        .andExpect(jsonPath("$.data.analysisType").value("DOMEME"))
        .andExpect(jsonPath("$.data.converted").value(true))
        .andExpect(jsonPath("$.data.resolvedUrl").value(DOMEME))
        .andExpect(jsonPath("$.data.optionSelectionRequired").value(true))
        .andExpect(jsonPath("$.data.options[0].price").value(8000));
    resolve("https://domeggook.com/999").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.productId").value("999"))
        .andExpect(jsonPath("$.data.converted").value(false))
        .andExpect(jsonPath("$.data.manualInputRequired").value(true))
        .andExpect(jsonPath("$.data.options").isEmpty());
    resolve(OWNERCLAN + "&ref=campaign#details").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.resolvedUrl").value(OWNERCLAN))
        .andExpect(jsonPath("$.data.optionSelectionRequired").value(false));
    resolve(OWNERCLAN + "&selfcode=another").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.supported").value(false));
  }

  @Test
  void validatesSelectionAndSharesQuotaStatusAndHistoryWithManualJobs() throws Exception {
    var owner = account();
    var other = account();
    mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body(DOMEGGOOK, null, null)))
        .andExpect(status().isUnauthorized());
    submit(owner, DOMEGGOOK, null, null).andExpect(status().isBadRequest());
    submit(owner, DOMEGGOOK, null, "SMALL").andExpect(status().isBadRequest());
    submit(owner, "https://example.com/123", null, null).andExpect(status().isBadRequest());

    long id = response(submit(owner, DOMEGGOOK, " lantern ", "RED").andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.source").value("DOMEME"))
        .andExpect(jsonPath("$.data.productId").value("12345678"))
        .andExpect(jsonPath("$.data.keyword").value("lantern"))
        .andExpect(jsonPath("$.data.selectedOptionId").value("RED"))
        .andExpect(jsonPath("$.data.analysisStatus").value("PENDING"))).at("/data/analysisId").longValue();
    assertThat(usage.current(owner.id()).usedCount()).isZero();
    mvc.perform(post("/search/analyses/manual").header("Authorization", owner.bearer())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"productName":"Manual","wholesalePrice":8000,"shippingFee":0,"hasPricePolicy":false}
                """))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("R002"));
    mvc.perform(get(STATUS, id).header("Authorization", other.bearer())).andExpect(status().isNotFound());

    worker.runNext();
    long reportId = response(mvc.perform(get(STATUS, id).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("COMPLETED")))
        .at("/data/reportId").longValue();
    mvc.perform(get("/search/reports/{id}", reportId).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisType").value("URL"))
        .andExpect(jsonPath("$.data.product.originalUrl").value(DOMEGGOOK))
        .andExpect(jsonPath("$.data.product.resolvedUrl").value(DOMEME))
        .andExpect(jsonPath("$.data.product.selectedOption.optionId").value("RED"));
    mvc.perform(get("/search/reports/{id}", reportId).header("Authorization", other.bearer()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/search/analyses/recent").header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analyses[0].analysisType").value("URL"))
        .andExpect(jsonPath("$.data.analyses[0].source").value("DOMEME"))
        .andExpect(jsonPath("$.data.analyses[0].productId").value("12345678"));
    assertThat(usage.current(owner.id()).usedCount()).isEqualTo(1);
    submit(owner, DOMEGGOOK, null, "RED").andExpect(status().isTooManyRequests());
  }

  @Test
  void failureAndDataLabExemptionDoNotCharge() throws Exception {
    var owner = account();
    long failed = response(submit(owner, OWNERCLAN, "FAIL", null).andExpect(status().isAccepted()))
        .at("/data/analysisId").longValue();
    worker.runNext();
    mvc.perform(get(STATUS, failed).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("FAILED"));
    long exempt = response(submit(owner, OWNERCLAN, "EXEMPT", null).andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.productName").isEmpty())).at("/data/analysisId").longValue();
    worker.runNext();
    mvc.perform(get(STATUS, exempt).header("Authorization", owner.bearer()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.analysisStatus").value("COMPLETED"));
    assertThat(usage.current(owner.id()).remainingCount()).isEqualTo(1);
  }

  private ResultActions resolve(String url) throws Exception {
    return mvc.perform(post(RESOLVE).contentType(MediaType.APPLICATION_JSON)
        .content(mapper.createObjectNode().put("url", url).toString()));
  }

  private ResultActions submit(Account owner, String url, String keyword, String option) throws Exception {
    return mvc.perform(post(SUBMIT).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
        .content(body(url, keyword, option)));
  }

  private String body(String url, String keyword, String option) {
    return mapper.createObjectNode().put("url", url).put("keyword", keyword).put("selectedOptionId", option).toString();
  }
}
