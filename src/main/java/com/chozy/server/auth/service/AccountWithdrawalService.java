package com.chozy.server.auth.service;

import com.chozy.server.auth.repository.RefreshTokenRepository;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class AccountWithdrawalService {
  private final MemberAccess members;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public AccountWithdrawalService(MemberAccess members, RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder, Clock clock) {
    this.members = members;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
    this.clock = clock;
  }

  @Transactional
  public void withdraw(AuthenticatedMember principal, String password) {
    var member = members.lockCurrent(principal);
    if (!PasswordPolicy.fitsBcrypt(password) || !encoder.matches(password, member.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    member.withdraw(clock.instant());
    refreshTokens.revokeAllByMemberId(member.getId());
  }
}
