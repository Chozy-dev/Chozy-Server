package com.chozy.server.member.repository;

import java.time.OffsetDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class MemberActivityRepository {
  private final JdbcClient jdbc;

  public MemberActivityRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public long countScreeningRuns(long memberId, OffsetDateTime start, OffsetDateTime end) {
    return jdbc.sql("""
        SELECT count(*) FROM chozy.member_screening_runs
        WHERE member_id = :memberId AND executed_at >= :start AND executed_at < :end
        """).param("memberId", memberId).param("start", start).param("end", end)
        .query(Long.class).single();
  }

  public long countFavorites(long memberId) {
    return jdbc.sql("SELECT count(*) FROM chozy.member_favorites WHERE member_id = :memberId")
        .param("memberId", memberId).query(Long.class).single();
  }
}
