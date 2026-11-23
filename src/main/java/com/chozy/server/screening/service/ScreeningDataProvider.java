package com.chozy.server.screening.service;

import com.chozy.server.screening.dto.Screenings.CompetitionLevel;
import com.chozy.server.screening.dto.Screenings.Daily;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Boundary for Search Ads and DataLab calls. Each daily series uses a common scale from
 * endDate.minusYears(1).minusDays(27) through endDate, both inclusive.
 */
public interface ScreeningDataProvider {
  /** Every requested keyword requires ads metrics. An unavailable search trend is an empty series. */
  SearchBatch search(List<String> keywords, LocalDate endDate);

  /** Returns daily click indices through endDate, or an empty series when the trend is unavailable. */
  List<Daily> shopping(String keyword, String categoryCode, LocalDate endDate);

  record SearchBatch(Map<String, SearchData> keywords, String notice) {}
  record SearchData(AdMetrics ads, List<Daily> searchTrend) {}

  /** Counts replace each source's suppressed '<10' value with 5 and set searchCountEstimated. Rates are percent. */
  record AdMetrics(long pcSearchCount, long mobileSearchCount, boolean searchCountEstimated,
      BigDecimal pcClickRate, BigDecimal mobileClickRate, CompetitionLevel competitionLevel,
      BigDecimal averageAdDepth) {}
}
