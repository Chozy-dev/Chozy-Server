package com.chozy.server.home.repository;

import static com.chozy.server.home.dto.RecommendedProducts.Source.DOMEME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

class CollectorRecommendedProductReaderTest {
  private static final String SQL = "SELECT public.read_supply_home(?, ?)::text";
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CollectorRecommendedProductReader reader =
      new CollectorRecommendedProductReader(jdbc, new ObjectMapper().findAndRegisterModules());

  @Test
  void mapsCompletedPublicationUsingItsUpdatedAtAndPublishedRanks() {
    when(jdbc.queryForObject(SQL, String.class, "DOMEME", 10)).thenReturn("""
        {
          "updatedAt":"2026-11-25T05:00:00+00:00","asOf":"2026-11-24T15:00:00+00:00",
          "batchId":"batch-1","formulaVersion":"supply_v2","items":[
            {"rank":1,"productId":10001,"productName":"랜턴","productUrl":"https://example.com/42",
             "source":"DOMEME","supplyPrice":8000,"stockoutCount90d":0,"supplierStockoutRate90d":null,
             "score":97,"externalProductId":"42","eligible":true}
          ]
        }
        """);

    var publication = reader.latest(DOMEME, 10).orElseThrow();
    assertThat(publication.updatedAt().toInstant()).isEqualTo(Instant.parse("2026-11-25T05:00:00Z"));
    assertThat(publication.items()).singleElement().satisfies(item -> {
      assertThat(item.rank()).isEqualTo(1);
      assertThat(item.productId()).isEqualTo(10001);
      assertThat(item.stockoutCount90d()).isZero();
      assertThat(item.supplierStockoutRate90d()).isNull();
      assertThat(item.score()).isEqualByComparingTo("97");
    });
  }

  @Test
  void distinguishesAnEmptyPublicationFromNoPublication() {
    when(jdbc.queryForObject(SQL, String.class, "DOMEME", 10))
        .thenReturn(null, "{\"updatedAt\":\"2026-11-25T05:00:00+00:00\",\"items\":[]}");

    assertThat(reader.latest(DOMEME, 10)).isEmpty();
    assertThat(reader.latest(DOMEME, 10).orElseThrow().items()).isEmpty();
  }

  @Test
  void reportsConnectionFailureAsRecommendationsUnavailable() {
    when(jdbc.queryForObject(SQL, String.class, "DOMEME", 10))
        .thenThrow(new CannotGetJdbcConnectionException("unavailable"));

    assertThatThrownBy(() -> reader.latest(DOMEME, 10)).isInstanceOfSatisfying(BusinessException.class,
        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.RECOMMENDATIONS_UNAVAILABLE));
  }
}
