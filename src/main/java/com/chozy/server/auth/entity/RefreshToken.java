package com.chozy.server.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", schema = "chozy")
public class RefreshToken {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "member_id", nullable = false) private Long memberId;
  @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  protected RefreshToken() {}
  public RefreshToken(long memberId, String hash, Instant expiresAt) {
    this.memberId = memberId;
    this.tokenHash = hash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }
  public Long getMemberId() { return memberId; }
  public boolean usable(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
  public void revoke() { revokedAt = Instant.now(); }
}
