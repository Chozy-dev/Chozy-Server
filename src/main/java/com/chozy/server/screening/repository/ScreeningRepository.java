package com.chozy.server.screening.repository;

import com.chozy.server.screening.dto.Screenings.Keyword;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"database", "dbdev"})
public class ScreeningRepository {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final TypeReference<List<Keyword>> SNAPSHOT = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  public ScreeningRepository(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public long recordMemberRun(long memberId, OffsetDateTime analyzedAt) {
    return jdbc.sql("""
        INSERT INTO chozy.member_screening_runs (member_id, executed_at) VALUES (:member, :analyzedAt)
        RETURNING id
        """).param("member", memberId).param("analyzedAt", analyzedAt).query(Long.class).single();
  }

  public void save(UUID id, Long memberRunId, OffsetDateTime analyzedAt, LocalDate trendEndDate, OffsetDateTime expiresAt,
      List<Keyword> keywords) {
    jdbc.sql("""
        INSERT INTO chozy.screening_results (public_id, member_run_id, analyzed_at, trend_end_date, expires_at, keywords)
        VALUES (:id, :run, :analyzedAt, :endDate, :expiresAt, CAST(:keywords AS jsonb))
        """).param("id", id).param("run", memberRunId).param("analyzedAt", analyzedAt)
        .param("endDate", trendEndDate).param("expiresAt", expiresAt).param("keywords", snapshot(keywords)).update();
  }

  public void deleteExpiredGuests(OffsetDateTime now) {
    jdbc.sql("""
        DELETE FROM chozy.screening_results WHERE member_run_id IS NULL AND expires_at <= :now
        """).param("now", now).update();
  }

  public Optional<Stored> latest(long memberId) {
    return jdbc.sql("""
        SELECT r.public_id, r.analyzed_at, r.trend_end_date, CAST(r.keywords AS text) AS keywords
        FROM chozy.member_screening_runs m
        JOIN chozy.screening_results r ON r.member_run_id = m.id
        WHERE m.member_id = :member
        ORDER BY m.executed_at DESC, m.id DESC LIMIT 1
        """).param("member", memberId).query(this::stored).optional();
  }

  public Optional<Stored> findAccessible(UUID id, Long memberId, OffsetDateTime now) {
    return jdbc.sql("""
        SELECT r.public_id, r.analyzed_at, r.trend_end_date, CAST(r.keywords AS text) AS keywords
        FROM chozy.screening_results r
        LEFT JOIN chozy.member_screening_runs m ON m.id = r.member_run_id
        WHERE r.public_id = :id
          AND (m.member_id = :member OR (r.member_run_id IS NULL AND r.expires_at > :now))
        """).param("id", id).param("member", memberId).param("now", now).query(this::stored).optional();
  }

  private Stored stored(ResultSet row, int index) throws SQLException {
    try {
      var at = row.getObject("analyzed_at", OffsetDateTime.class).atZoneSameInstant(KOREA).toOffsetDateTime();
      return new Stored(row.getObject("public_id", UUID.class), at, row.getObject("trend_end_date", LocalDate.class),
          mapper.readValue(row.getString("keywords"), SNAPSHOT));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Invalid stored screening snapshot", ex);
    }
  }

  private String snapshot(List<Keyword> keywords) {
    try {
      return mapper.writeValueAsString(keywords);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not encode screening snapshot", ex);
    }
  }

  public record Stored(UUID id, OffsetDateTime analyzedAt, LocalDate trendEndDate, List<Keyword> keywords) {}
}
