package com.chozy.server.screening.service;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.screening.dto.Screenings.Categories;
import com.chozy.server.screening.dto.Screenings.Category;
import java.util.List;

public final class ShoppingCategories {
  private static final Categories CATALOG = new Categories(List.of(
      new Category("50000000", "패션의류"),
      new Category("50000001", "패션잡화"),
      new Category("50000002", "화장품/미용"),
      new Category("50000003", "디지털/가전"),
      new Category("50000004", "가구/인테리어"),
      new Category("50000005", "출산/육아"),
      new Category("50000006", "식품"),
      new Category("50000007", "스포츠/레저"),
      new Category("50000008", "생활/건강"),
      new Category("50000009", "여가/생활편의"),
      new Category("50005542", "도서")));

  private ShoppingCategories() {}

  public static Categories list() {
    return CATALOG;
  }

  public static Category require(String code) {
    return CATALOG.categories().stream().filter(category -> category.categoryCode().equals(code)).findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
  }
}
