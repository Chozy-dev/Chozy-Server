package com.chozy.server.auth.service;

import com.chozy.server.auth.repository.RefreshTokenRepository;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.service.MemberAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class PasswordChangeService {
  private final MemberAccess members;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;

  public PasswordChangeService(MemberAccess members, RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder) {
    this.members = members;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
  }

  @Transactional
  public void change(AuthenticatedMember principal, String currentPassword, String newPassword) {
    var member = members.lockCurrent(principal);
    if (!PasswordPolicy.fitsBcrypt(currentPassword) || !PasswordPolicy.fitsBcrypt(newPassword)
        || !encoder.matches(currentPassword, member.getPasswordHash())
        || currentPassword.equals(newPassword)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    }
    member.changePassword(encoder.encode(newPassword));
    refreshTokens.revokeAllByMemberId(member.getId());
  }
}
