package com.chozy.server.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.ProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ProbeController.class)
class GlobalExceptionHandlerTest {
  @Autowired MockMvc mvc;

  @Test
  @WithMockUser
  void validationNeverReturnsRejectedInput() throws Exception {
    mvc.perform(
            post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"secret\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C001"))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  @WithMockUser
  void malformedJson() throws Exception {
    mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C009"));
  }

  @Test
  @WithMockUser
  void typeAndMissingParameter() throws Exception {
    mvc.perform(get("/probe/number").param("value", "secret-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C005"));
    mvc.perform(get("/probe/number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("C008"));
  }

  @Test
  @WithMockUser
  void methodMediaAndNotFound() throws Exception {
    mvc.perform(delete("/probe/body"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("C007"))
        .andExpect(header().exists("Allow"));
    mvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("input"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("C010"));
    mvc.perform(get("/absent"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("C003"));
  }

  @Test
  @WithMockUser
  void businessForbiddenAndUnexpected() throws Exception {
    mvc.perform(get("/probe/business"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("C002"));
    mvc.perform(get("/probe/forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("C006"));
    mvc.perform(get("/probe/failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("C004"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
  }
}
