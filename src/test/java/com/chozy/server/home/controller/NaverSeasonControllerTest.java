package com.chozy.server.home.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonKeyword;
import com.chozy.server.home.dto.NaverSeasonKeywords.SeasonTheme;
import com.chozy.server.home.service.NaverSeasonReader;
import com.chozy.server.home.service.NaverSeasonReader.ComparedPublication;
import com.chozy.server.home.service.NaverSeasonReader.Publication;
import com.chozy.server.home.service.NaverSeasonReader.PublishedKeyword;
import com.chozy.server.home.service.NaverSeasonReader.PublishedTheme;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
class NaverSeasonControllerTest {
  private static final String NAVER = "/home/keywords/naver";
  private static final String COMPARE = "/home/keywords/compare";
  private static final LocalDate DATE = LocalDate.parse("2026-11-14");
  private static final OffsetDateTime NAVER_AT = OffsetDateTime.parse("2026-11-14T03:00:00+09:00");
  private static final OffsetDateTime COUPANG_AT = NAVER_AT.plusHours(1);

  @Autowired MockMvc mvc;
  @MockitoBean NaverSeasonReader reader;

  @Test
  void selectsTheDefaultThemeAndLimitsPublishedRankOrderToTen() throws Exception {
    var keywords = IntStream.rangeClosed(1, 11)
        .mapToObj(rank -> new PublishedKeyword(new SeasonKeyword(rank, "키워드" + rank, 100000L - rank, null), null))
        .toList();
    var themes = List.of(new PublishedTheme(new SeasonTheme(8, "냉방"), List.of()),
        new PublishedTheme(new SeasonTheme(6, "난방/방한"), keywords));
    when(reader.latest(false)).thenReturn(Optional.of(new Publication(DATE, NAVER_AT, themes, null)));

    mvc.perform(get(NAVER)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(NAVER_AT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
        .andExpect(jsonPath("$.data.themes[*].themeCode").value(contains(6, 8)))
        .andExpect(jsonPath("$.data.selectedThemeCode").value(6))
        .andExpect(jsonPath("$.data.items.length()").value(10))
        .andExpect(jsonPath("$.data.items[0].monthlySearchCount").value(99999))
        .andExpect(jsonPath("$.data.items[0].growthRate").value(nullValue()))
        .andExpect(jsonPath("$.data.items[9].rank").value(10));
    mvc.perform(get(NAVER).param("themeCode", "7")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.selectedThemeCode").value(7))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void separatesPositiveAndZeroMatchesForTheSameSnapshotDate() throws Exception {
    when(reader.latest(true)).thenReturn(Optional.of(publication(new ComparedPublication(DATE, COUPANG_AT))));

    mvc.perform(get(COMPARE).param("themeCode", "6")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(COUPANG_AT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
        .andExpect(jsonPath("$.data.themeCode").value(6))
        .andExpect(jsonPath("$.data.themeName").value("난방/방한"))
        .andExpect(jsonPath("$.data.matchedKeywords[0].keyword").value("전기요"))
        .andExpect(jsonPath("$.data.matchedKeywords[0].growthRate").value(64.2))
        .andExpect(jsonPath("$.data.matchedKeywords[0].matchedProductCount").value(3))
        .andExpect(jsonPath("$.data.unmatchedKeywords[0].keyword").value("문풍지"))
        .andExpect(jsonPath("$.data.unmatchedKeywords[0].matchedProductCount").value(0));
  }

  @Test
  void neverUsesAnOlderCoupangBatchOrReportsItsKeywordsAsUnmatched() throws Exception {
    when(reader.latest(true)).thenReturn(Optional.of(publication(new ComparedPublication(DATE.minusDays(1), COUPANG_AT))));

    mvc.perform(get(COMPARE)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(NAVER_AT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
        .andExpect(jsonPath("$.data.themeCode").value(6))
        .andExpect(jsonPath("$.data.matchedKeywords").isEmpty())
        .andExpect(jsonPath("$.data.unmatchedKeywords").isEmpty())
        .andExpect(jsonPath("$.data.notice").value("같은 기준일의 쿠팡 베스트 발행본이 없어 비교할 수 없습니다."));
  }

  @Test
  void returnsEmptyDataWhenThereIsNoNaverPublication() throws Exception {
    mvc.perform(get(NAVER)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
        .andExpect(jsonPath("$.data.themes").isEmpty())
        .andExpect(jsonPath("$.data.selectedThemeCode").value(nullValue()));
    mvc.perform(get(COMPARE)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.themeCode").value(nullValue()))
        .andExpect(jsonPath("$.data.notice").value("완료된 네이버 시즌 키워드 발행본이 없습니다."));
  }

  @ParameterizedTest
  @CsvSource({"/home/keywords/naver,0,C001", "/home/keywords/compare,1.5,C005"})
  void invalidThemeCodesDoNotReadPublications(String path, String code, String error) throws Exception {
    mvc.perform(get(path).param("themeCode", code)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(error));
    verifyNoInteractions(reader);
  }

  private Publication publication(ComparedPublication comparison) {
    var matched = new PublishedKeyword(new SeasonKeyword(1, "전기요", 180000L, new BigDecimal("64.2")), 3L);
    var unmatched = new PublishedKeyword(new SeasonKeyword(2, "문풍지", 55000L, null), 0L);
    return new Publication(DATE, NAVER_AT,
        List.of(new PublishedTheme(new SeasonTheme(6, "난방/방한"), List.of(matched, unmatched))), comparison);
  }
}
