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

  protected Member() {}

  /** Accepts an encoded password only. Verification and signup are future service responsibilities. */
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

  public Long getId() { return id; }
  public String getLoginId() { return loginId; }
  public String getEmail() { return email; }
}
