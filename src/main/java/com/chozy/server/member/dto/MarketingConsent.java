package com.chozy.server.member.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record MarketingConsent(boolean agreed,
    @Schema(types = {"string", "null"}, format = "date-time",
        description = "마지막 동의 상태 변경 시각. 변경 이력이 없으면 null", requiredMode = Schema.RequiredMode.REQUIRED)
    OffsetDateTime updatedAt) {
  public record Change(@Schema(description = "true는 동의, false는 철회", example = "true")
      @JsonDeserialize(using = MemberRequestJson.Bool.class) @NotNull Boolean agreed) {}
}
