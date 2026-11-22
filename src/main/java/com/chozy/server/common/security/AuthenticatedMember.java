package com.chozy.server.common.security;

/** Use @AuthenticationPrincipal AuthenticatedMember in protected controllers. */
public record AuthenticatedMember(long memberId) {}
