package com.chozy.server;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:postgresql://invalid-do-not-connect/test")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ChozyServerApplicationTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;

  @Test
  void noDatabaseInfrastructure() {
    assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
    assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
    assertThat(
            context.getBeansOfType(
                org.springframework.security.core.userdetails.UserDetailsService.class))
        .isEmpty();
  }

  @Test
  void docsExposeTheDatabaseFreePublicOperations() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Chozy API"))
        .andExpect(jsonPath("$.paths", aMapWithSize(3)))
        .andExpect(jsonPath("$.paths['/search/categories'].get.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/search/coupang-categories'].get.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/search/coupang-categories'].get.security").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.CoupangCategory.properties.feeRate.type").value("number"))
        .andExpect(jsonPath("$.paths['/search/products/resolve-url'].post.responses['200']").exists());
    mvc.perform(get("/search/categories")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.categories.length()").value(11));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void listsTheCoupangCategoriesAndVatInclusiveRatesWithoutAuthentication() throws Exception {
    mvc.perform(get("/search/coupang-categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.message").value("쿠팡 카테고리 목록 조회가 완료되었습니다."))
        .andExpect(jsonPath("$.data.categories[*].categoryCode", contains(
            "ELECTRONICS", "FURNITURE", "BOOKS", "MUSIC", "STATIONERY", "BABY", "SPORTS", "BEAUTY",
            "LIVING", "FOOD", "TOYS", "AUTOMOTIVE", "KITCHEN", "FASHION", "PETS")))
        .andExpect(jsonPath("$.data.categories[0].categoryName").value("가전디지털"))
        .andExpect(jsonPath("$.data.categories[*].feeRate", contains(
            8.58, 11.88, 11.88, 11.88, 11.88, 11.0, 11.88, 10.56, 8.58, 11.66, 11.88, 11.0, 11.88, 11.55, 11.88)));
  }
}
