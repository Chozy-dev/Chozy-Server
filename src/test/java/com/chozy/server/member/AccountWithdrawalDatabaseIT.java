package com.chozy.server.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.support.AccountPostgresITSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AccountWithdrawalDatabaseIT extends AccountPostgresITSupport {
  @Test
  void correctPasswordWithdrawsOnlyTheCallerAndRevokesAllSessions() throws Exception {
    var member = account();
    var other = account();
    var second = auth.login(new Login(member.loginId(), PASSWORD));
    withdraw(member, PASSWORD).andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("회원탈퇴가 완료되었습니다."))
        .andExpect(jsonPath("$.data").value(nullValue()));
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.members WHERE id = :id AND withdrawn_at IS NOT NULL")
        .param("id", member.id()).query(Long.class).single()).isEqualTo(1L);
    assertThat(jdbc.sql("SELECT count(*) FROM chozy.refresh_tokens WHERE member_id = :id AND revoked_at IS NULL")
        .param("id", member.id()).query(Long.class).single()).isZero();
    mvc.perform(get("/my/dashboard").header("Authorization", member.bearer()))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    mvc.perform(get("/my/dashboard").header("Authorization", "Bearer " + second.accessToken()))
        .andExpect(status().isUnauthorized());
    withdraw(member, PASSWORD).andExpect(status().isUnauthorized());
    reissue(member.tokens().refreshToken()).andExpect(status().isUnauthorized());
    reissue(second.refreshToken()).andExpect(status().isUnauthorized());
    mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("loginId", member.loginId(), "password", PASSWORD))))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A004"));
    mvc.perform(get("/my/dashboard").header("Authorization", other.bearer())).andExpect(status().isOk());
    reissue(other.tokens().refreshToken()).andExpect(status().isOk());
  }

  @Test
  void invalidPasswordsLeaveTheAccountAndTokensActive() throws Exception {
    var member = account();
    withdraw(member, "wrong").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
    withdraw(member, 123).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C009"));
    mvc.perform(get("/my/dashboard").header("Authorization", member.bearer())).andExpect(status().isOk());
    reissue(member.tokens().refreshToken()).andExpect(status().isOk());
  }

  private ResultActions withdraw(Account member, Object password) throws Exception {
    return mvc.perform(delete("/my/account").header("Authorization", member.bearer())
        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("password", password))));
  }

  private ResultActions reissue(String token) throws Exception {
    return mvc.perform(post("/auth/reissue").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("refreshToken", token))));
  }
}
