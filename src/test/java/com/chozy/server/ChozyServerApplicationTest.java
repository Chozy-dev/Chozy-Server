package com.chozy.server;

import static org.assertj.core.api.Assertions.*;
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
  void docsAreRealAndBusinessPathsAbsent() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Chozy API"))
        .andExpect(jsonPath("$.paths").isEmpty());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
