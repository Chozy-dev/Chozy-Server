package com.chozy.server.usage.service;

import java.time.*;

public record UsageMonth(YearMonth period) {
  public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  public static UsageMonth at(Instant now) {
    return new UsageMonth(YearMonth.from(now.atZone(KOREA)));
  }

  public LocalDate startDate() {
    return period.atDay(1);
  }

  public OffsetDateTime startsAt() {
    return startDate().atStartOfDay(KOREA).toOffsetDateTime();
  }

  public OffsetDateTime nextResetAt() {
    return period.plusMonths(1).atDay(1).atStartOfDay(KOREA).toOffsetDateTime();
  }
}
