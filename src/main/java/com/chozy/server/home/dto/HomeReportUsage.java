package com.chozy.server.home.dto;

import com.chozy.server.usage.service.ReportUsage;

public record HomeReportUsage(boolean isLoggedIn, String plan, String period, String periodType,
    long usedCount, int limitCount, long remainingCount) {
  public static HomeReportUsage from(ReportUsage usage) {
    return new HomeReportUsage(true, "FREE", usage.period(), usage.periodType(), usage.usedCount(),
        usage.limitCount(), usage.remainingCount());
  }
}
