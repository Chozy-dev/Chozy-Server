package com.chozy.server.analysis.service;

import com.chozy.server.usage.service.ReportQuotaExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"database", "dbdev"})
@EnableScheduling
public class ManualAnalysisWorker {
  private static final Logger log = LoggerFactory.getLogger(ManualAnalysisWorker.class);

  private final ManualAnalysisService service;
  private final ObjectProvider<ManualAnalysisEngine> engines;

  public ManualAnalysisWorker(ManualAnalysisService service, ObjectProvider<ManualAnalysisEngine> engines) {
    this.service = service;
    this.engines = engines;
  }

  @Scheduled(cron = "${app.analysis.worker-cron:*/1 * * * * *}")
  public void runNext() {
    var engine = engines.getIfAvailable();
    if (engine == null) return;

    var claimed = service.claim();
    if (claimed.isEmpty()) return;
    var job = claimed.get();
    try {
      service.complete(job, engine.analyze(job.id(), job.input()));
    } catch (ReportQuotaExceededException ex) {
      service.fail(job, "이번 달 무료 리포트 횟수를 모두 사용했습니다.");
    } catch (RuntimeException ex) {
      log.warn("Manual analysis failed id={} type={}", job.id(), ex.getClass().getName());
      service.fail(job, "정밀분석에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }
}
