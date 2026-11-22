package com.chozy.server.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.security.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigurationTest {
  @Autowired MockMvc mvc;

  @ParameterizedTest
  @CsvSource({
    "/auth/logout,POST",
    "/home/products/recommended,GET",
    "/my/dashboard,GET",
    "/unimplemented,GET"
  })
  void anonymousIsBlocked(String path, String method) throws Exception {
    mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("A004"))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void fakeBearerIsNotAuthentication() throws Exception {
    mvc.perform(get("/my/dashboard").header("Authorization", "Bearer fake"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void corsPreflightIsRestricted() throws Exception {
    mvc.perform(
            options("/my/dashboard")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    mvc.perform(options("/my/profile")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "PATCH")
            .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().string("Access-Control-Allow-Headers", "Authorization, Content-Type"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    mvc.perform(
            options("/my/dashboard")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}
