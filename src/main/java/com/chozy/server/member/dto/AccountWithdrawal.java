package com.chozy.server.member.dto;

import static com.chozy.server.auth.service.PasswordPolicy.MAX_LENGTH;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountWithdrawal(
    @Schema(example = "ExamplePassword123!", format = "password", description = "현재 비밀번호")
    @JsonDeserialize(using = MemberRequestJson.Text.class)
    @NotBlank @Size(max = MAX_LENGTH) String password) {
  @Override
  public String toString() {
    return "AccountWithdrawal[redacted]";
  }
}
