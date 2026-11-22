package com.chozy.server.member.service;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.dto.ProfilePatch;
import com.chozy.server.member.dto.ProfileResult;
import com.chozy.server.usage.service.UsageMonth;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class MemberProfileService {
  private final MemberAccess members;
  private final Clock clock;

  public MemberProfileService(MemberAccess members, Clock clock) {
    this.members = members;
    this.clock = clock;
  }

  @Transactional
  public ProfileResult update(AuthenticatedMember principal, ProfilePatch request) {
    if (request.getNickname() == null && request.getEmail() == null)
      throw new BusinessException(ErrorCode.INVALID_INPUT);

    var member = members.lockCurrent(principal);
    var updatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
    member.updateProfile(request.getNickname(), request.getEmail(), updatedAt);
    return new ProfileResult(member.getId(), member.getNickname(), member.getEmail(),
        updatedAt.atZone(UsageMonth.KOREA).toOffsetDateTime());
  }
}
