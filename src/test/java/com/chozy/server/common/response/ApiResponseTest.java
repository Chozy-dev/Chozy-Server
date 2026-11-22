package com.chozy.server.common.response;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.support.ProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ProbeController.class)
class ApiResponseTest {
  @Autowired MockMvc mvc;

  @Test
  @WithMockUser
  void successEnvelope() throws Exception {
    mvc.perform(get("/probe/success"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.message").value("요청이 완료되었습니다."))
        .andExpect(jsonPath("$.data.name").value("chozy"));
  }

  @Test
  void createdAndNullData() {
    assertThat(ApiResponse.success(201, "created", null).status()).isEqualTo(201);
    assertThatThrownBy(() -> ApiResponse.success(400, "bad", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
