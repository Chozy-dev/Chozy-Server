package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.UrlAnalyses.UrlInput;
import com.chozy.server.analysis.service.ManualAnalysisEngine.Result;

/** Computes a URL report from the validated supplier and selected option within the five-minute job lease. */
public interface UrlAnalysisEngine {
  Result analyze(long analysisId, UrlInput input);
}
