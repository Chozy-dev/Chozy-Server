package com.chozy.server.common.security;

import com.chozy.server.common.error.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;

/** HS256 only; signature, issuer, purpose and mandatory timestamps are checked before use. */
public final class JwtTokens {
  private final byte[] key;
  private final long accessSeconds;
  private final long refreshSeconds;
  private final Clock clock;
  public JwtTokens(String secret, long accessSeconds, long refreshSeconds, Clock clock) {
    try { key = Base64.getDecoder().decode(secret); }
    catch (Exception ex) { throw new IllegalArgumentException("JWT secret must be Base64"); }
    if (key.length < 32 || accessSeconds < 1 || refreshSeconds < 1)
      throw new IllegalArgumentException("JWT requires a 256-bit key and positive lifetimes");
    this.accessSeconds = accessSeconds;
    this.refreshSeconds = refreshSeconds;
    this.clock = clock;
  }
  public long accessSeconds() { return accessSeconds; }
  public String issue(long memberId, String type) {
    try {
      Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
      var claims = new JWTClaimsSet.Builder().issuer("chozy").subject(Long.toString(memberId))
          .jwtID(UUID.randomUUID().toString()).claim("type", type).issueTime(Date.from(now))
          .expirationTime(Date.from(now.plusSeconds(type.equals("access") ? accessSeconds : refreshSeconds))).build();
      var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
      jwt.sign(new MACSigner(key));
      return jwt.serialize();
    } catch (JOSEException ex) { throw new IllegalStateException("JWT signing failed"); }
  }
  public Verified verify(String token, String expectedType) {
    try {
      if (token == null || token.length() > 4096) throw new IllegalArgumentException();
      var jwt = SignedJWT.parse(token);
      if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key)))
        throw new IllegalArgumentException();
      var c = jwt.getJWTClaimsSet();
      Instant now = clock.instant();
      if (!"chozy".equals(c.getIssuer()) || !expectedType.equals(c.getStringClaim("type"))
          || c.getExpirationTime() == null || c.getIssueTime() == null || c.getJWTID() == null
          || c.getIssueTime().toInstant().isAfter(now)
          || !c.getExpirationTime().after(c.getIssueTime())) throw new IllegalArgumentException();
      long id = Long.parseLong(c.getSubject());
      if (id <= 0) throw new IllegalArgumentException();
      if (!c.getExpirationTime().toInstant().isAfter(now))
        throw new BusinessException(expectedType.equals("access") ? ErrorCode.ACCESS_TOKEN_EXPIRED : ErrorCode.REFRESH_TOKEN_EXPIRED);
      return new Verified(id, c.getExpirationTime().toInstant());
    } catch (BusinessException ex) { throw ex; }
    catch (Exception ex) { throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION); }
  }
  public record Verified(long memberId, Instant expiresAt) {}
}
