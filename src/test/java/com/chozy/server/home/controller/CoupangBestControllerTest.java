package com.chozy.server.home.controller;

import static com.chozy.server.home.dto.CoupangBestProducts.RankStatus.NEW;
import static com.chozy.server.home.dto.CoupangBestProducts.RankStatus.UP;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestCategory;
import com.chozy.server.home.dto.CoupangBestProducts.CoupangBestProduct;
import com.chozy.server.home.service.CoupangBestReader;
import com.chozy.server.home.service.CoupangBestReader.Publication;
import com.chozy.server.home.service.CoupangBestReader.PublishedCategory;
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
class CoupangBestControllerTest {
  private static final String PATH = "/home/keywords/coupang";
  private static final String PUBLISHED_AT = "2026-11-13T03:00:00+09:00";

  @Autowired MockMvc mvc;
  @MockitoBean CoupangBestReader reader;

  @Test
  void choosesTheLowestPublishedCategoryAndReturnsItsRankedProducts() throws Exception {
    var tumbler = new CoupangBestProduct(1, 20001L, "스테인리스 텀블러", new BigDecimal("15900"), true, 3, 2, UP);
    var utensil = new CoupangBestProduct(2, 20002L, "실리콘 조리도구", new BigDecimal("9900"), false, null, null, NEW);
    var kitchen = new PublishedCategory(new CoupangBestCategory(1001, "주방용품"), List.of(tumbler, utensil));
    var living = new PublishedCategory(new CoupangBestCategory(1002, "생활용품"), List.of());
    when(reader.latest()).thenReturn(Optional.of(new Publication(OffsetDateTime.parse(PUBLISHED_AT), List.of(living, kitchen))));

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.message").value("쿠팡 베스트 상품 조회가 완료되었습니다."))
        .andExpect(jsonPath("$.data.updatedAt").value(PUBLISHED_AT))
        .andExpect(jsonPath("$.data.categories[*].categoryId").value(contains(1001, 1002)))
        .andExpect(jsonPath("$.data.categories[*].categoryName").value(contains("주방용품", "생활용품")))
        .andExpect(jsonPath("$.data.selectedCategoryId").value(1001))
        .andExpect(jsonPath("$.data.items[*].rank").value(contains(1, 2)))
        .andExpect(jsonPath("$.data.items[0].productId").value(20001))
        .andExpect(jsonPath("$.data.items[0].productName").value(tumbler.productName()))
        .andExpect(jsonPath("$.data.items[0].price").value(15900))
        .andExpect(jsonPath("$.data.items[*].isRocketDelivery").value(contains(true, false)))
        .andExpect(jsonPath("$.data.items[0].previousRank").value(3))
        .andExpect(jsonPath("$.data.items[0].rankChange").value(2))
        .andExpect(jsonPath("$.data.items[0].rankStatus").value("UP"))
        .andExpect(jsonPath("$.data.items[1].previousRank").value(nullValue()))
        .andExpect(jsonPath("$.data.items[1].rankChange").value(nullValue()))
        .andExpect(jsonPath("$.data.items[1].rankStatus").value("NEW"));
    verify(reader).latest();
  }

  @Test
  void selectsTheRequestedCategoryAndPreservesMissingPreviousDayData() throws Exception {
    var product = new CoupangBestProduct(1, 3_000_000_001L, "쿠션", new BigDecimal("9000.50"), false, null, null, null);
    var kitchen = new PublishedCategory(new CoupangBestCategory(1001, "주방용품"), List.of());
    var living = new PublishedCategory(new CoupangBestCategory(1002, "생활용품"), List.of(product));
    when(reader.latest()).thenReturn(Optional.of(new Publication(OffsetDateTime.parse(PUBLISHED_AT), List.of(kitchen, living))));

    mvc.perform(get(PATH).param("categoryId", "1002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.selectedCategoryId").value(1002))
        .andExpect(jsonPath("$.data.items[0].productId").value(3_000_000_001L))
        .andExpect(jsonPath("$.data.items[0].price").value(9000.50))
        .andExpect(jsonPath("$.data.items[0].previousRank").value(nullValue()))
        .andExpect(jsonPath("$.data.items[0].rankChange").value(nullValue()))
        .andExpect(jsonPath("$.data.items[0].rankStatus").value(nullValue()));
  }

  @Test
  void keepsTheRequestedIdAndLatestPublicationWhenTheCategoryIsAbsent() throws Exception {
    var item = new CoupangBestProduct(1, 20001L, "텀블러", new BigDecimal("15900"), true, 3, 2, UP);
    var kitchen = new PublishedCategory(new CoupangBestCategory(1001, "주방용품"), List.of(item));
    when(reader.latest()).thenReturn(Optional.of(new Publication(OffsetDateTime.parse(PUBLISHED_AT), List.of(kitchen))));

    mvc.perform(get(PATH).param("categoryId", "1002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(PUBLISHED_AT))
        .andExpect(jsonPath("$.data.categories[*].categoryId").value(contains(1001)))
        .andExpect(jsonPath("$.data.selectedCategoryId").value(1002))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void returnsEmptyListsAndNullSelectionWhenNoPublicationExists() throws Exception {
    when(reader.latest()).thenReturn(Optional.empty());

    mvc.perform(get(PATH).param("categoryId", "1002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
        .andExpect(jsonPath("$.data.categories").isEmpty())
        .andExpect(jsonPath("$.data.selectedCategoryId").value(nullValue()))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @ParameterizedTest
  @CsvSource({"0,C001", "-1,C001", "1.5,C005", "not-a-number,C005"})
  void rejectsInvalidCategoryIdsBeforeReadingPublications(String categoryId, String code) throws Exception {
    mvc.perform(get(PATH).param("categoryId", categoryId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(code));
    verifyNoInteractions(reader);
  }

  @Test
  void documentsThePublicCategoryFilterAndNullableRankStatus() throws Exception {
    String operation = "$['paths']['" + PATH + "']['get']";
    String rankStatus = "$.components.schemas.CoupangBestProduct.properties.rankStatus.anyOf";
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath(operation + ".security").doesNotExist())
        .andExpect(jsonPath(operation + ".parameters.length()").value(1))
        .andExpect(jsonPath(operation + ".parameters[0].name").value("categoryId"))
        .andExpect(jsonPath(operation + ".parameters[0].required").value(false))
        .andExpect(jsonPath(operation + ".parameters[0].schema.minimum").value(1))
        .andExpect(jsonPath(operation + ".responses['503'].content['application/json'].examples.H002.value.code")
            .value("H002"))
        .andExpect(jsonPath(rankStatus + "[0].enum").value(containsInAnyOrder("UP", "DOWN", "SAME", "NEW")))
        .andExpect(jsonPath(rankStatus + "[1].type").value("null"));
  }
}
