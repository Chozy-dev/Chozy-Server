package com.chozy.server.member.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "members", schema = "chozy")
public class Member {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "login_id", nullable = false, length = 100)
  private String loginId;
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;
  @Column(nullable = false, length = 100)
  private String nickname;
  @Column(nullable = false, length = 320)
  private String email;
  @Column(name = "terms_agreed_at", nullable = false)
  private Instant termsAgreedAt;
  @Column(name = "privacy_agreed_at", nullable = false)
  private Instant privacyAgreedAt;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
  @Column(name = "verified_di_hash", length = 128)
  private String verifiedDiHash;
  @Column(name = "di_verified_at")
  private Instant diVerifiedAt;
  @Column(name = "credential_version", nullable = false)
  private long credentialVersion;
  @Column(name = "marketing_agreed", nullable = false)
  private boolean marketingAgreed;
  @Column(name = "marketing_updated_at")
  private Instant marketingUpdatedAt;
  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  protected Member() {}

  /** Accepts an encoded password only. Signup validation belongs to the auth service. */
  public Member(String loginId, String passwordHash, String nickname, String email,
      Instant termsAgreedAt, Instant privacyAgreedAt) {
    this.loginId = loginId.strip().toLowerCase(Locale.ROOT);
    this.passwordHash = passwordHash;
    this.nickname = nickname.strip();
    this.email = email.strip().toLowerCase(Locale.ROOT);
    this.termsAgreedAt = termsAgreedAt;
    this.privacyAgreedAt = privacyAgreedAt;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public String getPasswordHash() { return passwordHash; }
  public String getNickname() { return nickname; }
  public Instant getCreatedAt() { return createdAt; }

  public Long getId() { return id; }
  public String getLoginId() { return loginId; }
  public String getEmail() { return email; }
  public long getCredentialVersion() { return credentialVersion; }
  public boolean isMarketingAgreed() { return marketingAgreed; }
  public Instant getMarketingUpdatedAt() { return marketingUpdatedAt; }
  public boolean isActive() { return withdrawnAt == null; }

  public void updateProfile(String nickname, String email, Instant now) {
    if (nickname != null) this.nickname = nickname;
    if (email != null) this.email = email;
    updatedAt = now;
  }

  public void changePassword(String encodedPassword) {
    passwordHash = encodedPassword;
    credentialVersion++;
  }

  public void withdraw(Instant now) {
    withdrawnAt = now;
    updatedAt = now;
    credentialVersion++;
  }

  public void changeMarketingConsent(boolean agreed, Instant now) {
    marketingAgreed = agreed;
    marketingUpdatedAt = now;
  }
}
