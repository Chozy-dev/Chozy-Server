package com.chozy.server.auth.service;

import com.chozy.server.auth.dto.AuthDtos.*;
import com.chozy.server.auth.dto.AuthDtos;
import com.chozy.server.auth.entity.RefreshToken;
import com.chozy.server.auth.repository.RefreshTokenRepository;
import com.chozy.server.member.entity.Member;
import com.chozy.server.member.repository.MemberRepository;
import com.chozy.server.member.service.MemberAccess;
import com.chozy.server.common.error.*;
import com.chozy.server.common.security.AuthenticatedMember;
import com.chozy.server.common.security.JwtTokens;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"database", "dbdev"})
public class AuthService {
  private final MemberRepository members;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final JwtTokens jwt;
  private final SignupPolicy policy;
  private final MemberAccess memberAccess;
  private final String dummyHash;
  public AuthService(MemberRepository members, RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder, JwtTokens jwt, SignupPolicy policy, MemberAccess memberAccess) {
    this.members = members; this.refreshTokens = refreshTokens; this.encoder = encoder;
    this.jwt = jwt; this.policy = policy;
    this.memberAccess = memberAccess;
    dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
  }
  @Transactional
  public SignupResult signup(Signup request) {
    policy.requireAllowed();
    if (!PasswordPolicy.fitsBcrypt(request.password()))
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    if (members.existsByLoginId(request.loginId()) || members.existsByEmail(request.email()))
      throw new BusinessException(ErrorCode.DUPLICATE);
    Instant now = Instant.now();
    Member member = members.saveAndFlush(new Member(request.loginId(), encoder.encode(request.password()),
        request.nickname(), request.email(), now, now));
    return new SignupResult(member.getId(), member.getLoginId(), member.getNickname(), member.getEmail(), member.getCreatedAt());
  }
  @Transactional(readOnly = true)
  public Availability checkLoginId(String raw) {
    String id = AuthDtos.normalize(raw);
    if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,99}"))
      throw new BusinessException(ErrorCode.INVALID_INPUT);
    return new Availability(id, !members.existsByLoginId(id));
  }
  @Transactional
  public LoginResult login(Login request) {
    var member = members.lockByLoginId(request.loginId());
    boolean matches = PasswordPolicy.fitsBcrypt(request.password())
        && encoder.matches(request.password(), member.map(Member::getPasswordHash).orElse(dummyHash));
    if (member.isEmpty() || !matches) throw invalid();
    var m = member.orElseThrow();
    Tokens tokens = issue(m);
    return new LoginResult(m.getId(), m.getNickname(), tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn());
  }
  @Transactional
  public Tokens reissue(String raw) {
    var verified = jwt.verify(raw, "refresh");
    var member = memberAccess.lockCurrent(new AuthenticatedMember(
        verified.memberId(), verified.credentialVersion()));
    var stored = refreshTokens.lockByHash(hash(raw)).orElseThrow(AuthService::invalid);
    if (stored.getMemberId() != member.getId() || !stored.usable(Instant.now())) throw invalid();
    stored.revoke();
    return issue(member);
  }
  @Transactional
  public void logout(AuthenticatedMember principal, String raw) {
    var verified = jwt.verify(raw, "refresh");
    if (verified.memberId() != principal.memberId()) throw new BusinessException(ErrorCode.FORBIDDEN);
    memberAccess.lockCurrent(principal);
    if (verified.credentialVersion() != principal.credentialVersion()) throw invalid();
    var stored = refreshTokens.lockByHash(hash(raw)).orElseThrow(AuthService::invalid);
    if (stored.getMemberId() != principal.memberId() || !stored.usable(Instant.now())) throw invalid();
    stored.revoke();
  }
  private Tokens issue(Member member) {
    long id = member.getId();
    long version = member.getCredentialVersion();
    String access = jwt.issue(id, "access", version);
    String refresh = jwt.issue(id, "refresh", version);
    refreshTokens.save(new RefreshToken(id, hash(refresh), jwt.verify(refresh, "refresh").expiresAt()));
    return new Tokens(access, refresh, "Bearer", jwt.accessSeconds());
  }
  private static BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_AUTHENTICATION); }
  public static String hash(String raw) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
  }
}
