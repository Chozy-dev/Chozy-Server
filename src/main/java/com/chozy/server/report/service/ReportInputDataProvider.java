package com.chozy.server.report.service;

import com.chozy.server.analysis.dto.UrlAnalyses.Option;
import com.chozy.server.report.dto.ReportDetail;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Refreshes source data for changes that require looking beyond the saved report. */
public interface ReportInputDataProvider {
  /**
   * Replace affected source data in the draft, clearing data that is no longer applicable.
   * A keyword change refreshes keyword-dependent market fields. An option change refreshes
   * the affected supply fields and market comparison if the product specification changes.
   * An unavailable lookup must fail rather than leave the previous data in the draft.
   */
  void refresh(ReportDetail current, ObjectNode draft, Changes changes);

  record Changes(String keyword, Option option) {}
}
