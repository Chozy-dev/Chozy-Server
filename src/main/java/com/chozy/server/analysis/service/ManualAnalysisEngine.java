package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.ManualAnalysisInput;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Integration boundary for the market analysis and report calculations. */
public interface ManualAnalysisEngine {
  /**
   * Returns the report-detail snapshot, excluding server-owned identifiers and timestamps.
   * Implementations must bound their execution time to less than the job's five-minute lease.
   * Only authoritative shared DataLab-budget exhaustion permits an exempt result.
   */
  Result analyze(long analysisId, ManualAnalysisInput input);

  record Result(ObjectNode snapshot, boolean dataLabQuotaExhausted) {}
}
