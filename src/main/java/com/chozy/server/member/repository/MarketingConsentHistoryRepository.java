package com.chozy.server.member.repository;

import java.time.OffsetDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class MarketingConsentHistoryRepository {
  private final JdbcClient jdbc;

  public MarketingConsentHistoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void append(long memberId, boolean agreed, OffsetDateTime changedAt) {
    jdbc.sql("""
        INSERT INTO chozy.marketing_consent_history (member_id, agreed, changed_at)
        VALUES (:memberId, :agreed, :changedAt)
        """).param("memberId", memberId).param("agreed", agreed).param("changedAt", changedAt).update();
  }
}
