package com.chozy.server.home.controller;

import static com.chozy.server.home.dto.RecommendedProducts.Source.DOMEME;
import static com.chozy.server.home.dto.RecommendedProducts.Source.OWNERCLAN;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.home.dto.RecommendedProducts.RecommendedProduct;
import com.chozy.server.home.service.RecommendedProductReader;
import com.chozy.server.home.service.RecommendedProductReader.Publication;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RecommendedProductControllerTest {
  private static final String PATH = "/home/products/recommended";
  private static final String AS_OF = "2026-11-13T03:00:00+09:00";

  @Autowired MockMvc mvc;
  @MockitoBean RecommendedProductReader reader;

  @Test
  void listsTheRequestedSourcesPublishedItemsInRankOrderWithTheDefaultSize() throws Exception {
    var lantern = new RecommendedProduct(1, 10001L, "https://domeme.domeggook.com/12345678", "LED 캠핑 랜턴",
        DOMEME, new BigDecimal("8000"), new BigDecimal("94"), new BigDecimal("100"), 0,
        null, new BigDecimal("0.4"), new BigDecimal("97.25"));
    var charger = new RecommendedProduct(2, 10002L, "https://domeme.domeggook.com/23456789", "차량용 무선 충전기",
        DOMEME, new BigDecimal("12000"), new BigDecimal("90"), null, 1,
        null, null, new BigDecimal("91"));
    when(reader.latest(DOMEME, 10))
        .thenReturn(Optional.of(new Publication(OffsetDateTime.parse(AS_OF), List.of(lantern, charger))));

    mvc.perform(get(PATH).param("source", "DOMEME"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.message").value("공급 조건 우수 상품 조회가 완료되었습니다."))
        .andExpect(jsonPath("$.data.updatedAt").value(AS_OF))
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].rank").value(1))
        .andExpect(jsonPath("$.data.items[0].productId").value(10001))
        .andExpect(jsonPath("$.data.items[0].productUrl").value(lantern.productUrl()))
        .andExpect(jsonPath("$.data.items[0].productName").value(lantern.productName()))
        .andExpect(jsonPath("$.data.items[0].source").value("DOMEME"))
        .andExpect(jsonPath("$.data.items[0].supplyPrice").value(8000))
        .andExpect(jsonPath("$.data.items[0].supplyStabilityScore").value(94))
        .andExpect(jsonPath("$.data.items[0].supplierReliabilityScore").value(100))
        .andExpect(jsonPath("$.data.items[0].stockoutCount90d").value(0))
        .andExpect(jsonPath("$.data.items[0].supplierStockoutRate90d").value(nullValue()))
        .andExpect(jsonPath("$.data.items[0].shippingDays").value(0.4))
        .andExpect(jsonPath("$.data.items[0].score").value(97.25))
        .andExpect(jsonPath("$.data.items[1].rank").value(2))
        .andExpect(jsonPath("$.data.items[1].supplierReliabilityScore").value(nullValue()))
        .andExpect(jsonPath("$.data.items[1].shippingDays").value(nullValue()));
  }

  @Test
  void distinguishesAnEmptyPublicationFromTheAbsenceOfAPublication() throws Exception {
    when(reader.latest(OWNERCLAN, 1000))
        .thenReturn(Optional.of(new Publication(OffsetDateTime.parse(AS_OF), List.of())));
    when(reader.latest(DOMEME, 1)).thenReturn(Optional.empty());

    mvc.perform(get(PATH).param("source", "OWNERCLAN").param("size", "1000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(AS_OF))
        .andExpect(jsonPath("$.data.items").isEmpty());
    mvc.perform(get(PATH).param("source", "DOMEME").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void requiresASourceBeforeReadingPublications() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C008"));
    verifyNoInteractions(reader);
  }

  @ParameterizedTest
  @CsvSource({"MANUAL,10,C005", "DOMEME,0,C001", "DOMEME,1001,C001", "DOMEME,1.5,C005"})
  void rejectsInvalidInputsBeforeReadingPublications(String source, String size, String code) throws Exception {
    mvc.perform(get(PATH).param("source", source).param("size", size))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    verifyNoInteractions(reader);
  }

  @Test
  void documentsPublicQueryParametersAndThePublicationResponse() throws Exception {
    String operation = "$['paths']['" + PATH + "']['get']";
    String source = operation + ".parameters[?(@.name == 'source')]";
    String size = operation + ".parameters[?(@.name == 'size')]";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(operation + ".security").doesNotExist())
        .andExpect(jsonPath(source + ".required").value(containsInAnyOrder(true)))
        .andExpect(jsonPath(source + ".schema.enum[*]").value(containsInAnyOrder("DOMEME", "OWNERCLAN")))
        .andExpect(jsonPath(size + ".schema.default").value(containsInAnyOrder(10)))
        .andExpect(jsonPath(size + ".schema.minimum").value(containsInAnyOrder(1)))
        .andExpect(jsonPath(size + ".schema.maximum").value(containsInAnyOrder(1000)))
        .andExpect(jsonPath(operation + ".responses['503'].content['application/json'].examples.H001.value.code")
            .value("H001"))
        .andExpect(jsonPath("$.components.schemas.RecommendedProducts.properties.updatedAt.type")
            .value(containsInAnyOrder("string", "null")))
        .andExpect(jsonPath("$.components.schemas.RecommendedProduct.properties.shippingDays.type")
            .value(containsInAnyOrder("number", "null")))
        .andExpect(jsonPath("$.components.schemas.RecommendedProducts.properties.items.items['$ref']")
            .value("#/components/schemas/RecommendedProduct"));
  }
}
