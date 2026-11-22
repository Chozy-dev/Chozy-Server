package com.chozy.server.member.service;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.member.entity.Member;
import com.chozy.server.member.repository.MemberRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class MemberAccess {
  private final MemberRepository members;

  public MemberAccess(MemberRepository members) {
    this.members = members;
  }

  public boolean isCurrent(long memberId, long credentialVersion) {
    return members.existsByIdAndCredentialVersion(memberId, credentialVersion);
  }

  public Member requireCurrent(AuthenticatedMember principal) {
    return requireVersion(members.findById(principal.memberId()).orElseThrow(MemberAccess::invalid),
        principal.credentialVersion());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Member lock(long memberId) {
    return members.lockById(memberId).orElseThrow(MemberAccess::invalid);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Member lockCurrent(AuthenticatedMember principal) {
    return requireVersion(lock(principal.memberId()), principal.credentialVersion());
  }

  private static Member requireVersion(Member member, long version) {
    if (member.getCredentialVersion() != version) throw invalid();
    return member;
  }

  private static BusinessException invalid() {
    return new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
  }
}
