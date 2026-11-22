package com.chozy.server.usage.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.usage")
public record ReportUsageProperties(@Min(0) int freeMonthlyReportLimit) {}
