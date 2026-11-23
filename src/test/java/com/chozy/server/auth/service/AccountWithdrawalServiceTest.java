package com.chozy.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chozy.server.auth.dto.AuthDtos.Login;
import com.chozy.server.auth.repository.RefreshTokenRepository;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.common.security.JwtTokens;
import com.chozy.server.member.entity.Member;
import com.chozy.server.member.repository.MemberRepository;
import com.chozy.server.member.service.MemberAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AccountWithdrawalServiceTest {
  private static final String PASSWORD = "ExamplePassword123!";
  private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");
  private final AuthenticatedMember principal = new AuthenticatedMember(42, 0);
  private final MemberAccess members = mock(MemberAccess.class);
  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final AccountWithdrawalService service =
      new AccountWithdrawalService(members, refreshTokens, encoder, Clock.fixed(NOW, ZoneOffset.UTC));
  private final Member member = new Member("seller", encoder.encode(PASSWORD), "seller", "seller@example.com", NOW, NOW);

  @BeforeEach
  void memberIsAvailable() {
    ReflectionTestUtils.setField(member, "id", principal.memberId());
    when(members.lockCurrent(principal)).thenReturn(member);
  }

  @Test
  void disablesTheAccountAndRevokesItsRefreshTokens() {
    service.withdraw(principal, PASSWORD);

    assertThat(member.isActive()).isFalse();
    assertThat(member.getCredentialVersion()).isEqualTo(1);
    assertThat(ReflectionTestUtils.getField(member, "withdrawnAt")).isEqualTo(NOW);
    verify(refreshTokens).revokeAllByMemberId(principal.memberId());
  }

  @Test
  void wrongPasswordPreservesTheAccountAndRefreshTokens() {
    assertThatThrownBy(() -> service.withdraw(principal, "wrong"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

    assertThat(member.isActive()).isTrue();
    assertThat(member.getCredentialVersion()).isZero();
    verifyNoInteractions(refreshTokens);
  }

  @Test
  void withdrawnMemberCannotLogInEvenWithTheCorrectPassword() {
    member.withdraw(NOW);
    var repository = mock(MemberRepository.class);
    var jwt = mock(JwtTokens.class);
    var auth = new AuthService(repository, refreshTokens, encoder, jwt, mock(SignupPolicy.class), members);
    when(repository.lockByLoginId(member.getLoginId())).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> auth.login(new Login(member.getLoginId(), PASSWORD)))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_AUTHENTICATION));
    verifyNoInteractions(jwt, refreshTokens);
  }

  @Test
  void withdrawnMemberIsRejectedEvenWithTheLatestCredentialVersion() {
    member.withdraw(NOW);
    var repository = mock(MemberRepository.class);
    var access = new MemberAccess(repository);
    var latestPrincipal = new AuthenticatedMember(member.getId(), member.getCredentialVersion());
    when(repository.findById(member.getId())).thenReturn(Optional.of(member));
    when(repository.lockById(member.getId())).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> access.requireCurrent(latestPrincipal))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_AUTHENTICATION));
    assertThatThrownBy(() -> access.lockCurrent(latestPrincipal))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_AUTHENTICATION));
  }
}
