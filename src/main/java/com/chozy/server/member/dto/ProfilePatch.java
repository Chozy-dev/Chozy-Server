package com.chozy.server.member.dto;

import com.chozy.server.auth.dto.AuthDtos;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class ProfilePatch {
  @Schema(example = "전문셀러")
  @Size(min = 1, max = 100)
  private String nickname;
  @Schema(example = "seller@example.com", format = "email")
  @Email
  @Size(min = 1, max = 320)
  private String email;

  public String getNickname() {
    return nickname;
  }

  @JsonSetter(nulls = Nulls.FAIL)
  @JsonDeserialize(using = MemberRequestJson.Text.class)
  public void setNickname(String nickname) {
    this.nickname = nickname.strip();
  }

  public String getEmail() {
    return email;
  }

  @JsonSetter(nulls = Nulls.FAIL)
  @JsonDeserialize(using = MemberRequestJson.Text.class)
  public void setEmail(String email) {
    this.email = AuthDtos.normalize(email);
  }
}
