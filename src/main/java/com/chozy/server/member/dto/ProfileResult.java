package com.chozy.server.member.dto;

import java.time.OffsetDateTime;

public record ProfileResult(long userId, String nickname, String email, OffsetDateTime updatedAt) {}
