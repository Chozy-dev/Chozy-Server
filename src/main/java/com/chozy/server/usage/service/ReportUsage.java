package com.chozy.server.usage.service;

import java.time.OffsetDateTime;

public record ReportUsage(String period, String periodType, long usedCount, int limitCount,
    long remainingCount, OffsetDateTime nextResetAt) {}
