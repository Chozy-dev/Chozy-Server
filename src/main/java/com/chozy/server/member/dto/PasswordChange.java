package com.chozy.server.member.dto;

import static com.chozy.server.auth.service.PasswordPolicy.*;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChange(
    @Schema(example = "ExamplePassword123!", format = "password")
    @JsonDeserialize(using = MemberRequestJson.Text.class)
    @NotBlank @Size(max = MAX_LENGTH) String currentPassword,
    @Schema(example = "NewExamplePassword456!", format = "password", description = "UTF-8 기준 최대 72바이트")
    @JsonDeserialize(using = MemberRequestJson.Text.class)
    @NotBlank @Size(min = MIN_LENGTH, max = MAX_LENGTH) String newPassword) {
  @Override
  public String toString() {
    return "PasswordChange[redacted]";
  }
}
