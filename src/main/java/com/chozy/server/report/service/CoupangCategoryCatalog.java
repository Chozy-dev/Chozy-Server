package com.chozy.server.report.service;

import com.chozy.server.report.dto.CoupangCategories;
import com.chozy.server.report.dto.CoupangCategories.CoupangCategory;
import java.math.BigDecimal;
import java.util.List;

public final class CoupangCategoryCatalog {
  private static final CoupangCategories CATEGORIES = new CoupangCategories(List.of(
      category("ELECTRONICS", "가전디지털", "8.58"),
      category("FURNITURE", "가구/홈인테리어", "11.88"),
      category("BOOKS", "도서", "11.88"),
      category("MUSIC", "음반", "11.88"),
      category("STATIONERY", "문구/사무용품", "11.88"),
      category("BABY", "출산/유아", "11.00"),
      category("SPORTS", "스포츠/레저용품", "11.88"),
      category("BEAUTY", "뷰티", "10.56"),
      category("LIVING", "생활용품", "8.58"),
      category("FOOD", "식품", "11.66"),
      category("TOYS", "완구/취미", "11.88"),
      category("AUTOMOTIVE", "자동차용품", "11.00"),
      category("KITCHEN", "주방용품", "11.88"),
      category("FASHION", "패션", "11.55"),
      category("PETS", "반려/애완용품", "11.88")));

  private CoupangCategoryCatalog() {}

  public static CoupangCategories list() {
    return CATEGORIES;
  }

  private static CoupangCategory category(String code, String name, String feeRate) {
    return new CoupangCategory(code, name, new BigDecimal(feeRate));
  }
}
