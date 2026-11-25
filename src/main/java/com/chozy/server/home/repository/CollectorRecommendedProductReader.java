package com.chozy.server.home.repository;

import static com.chozy.server.common.error.ErrorCode.RECOMMENDATIONS_UNAVAILABLE;

import com.chozy.server.common.error.BusinessException;
import com.chozy.server.home.dto.RecommendedProducts.Source;
import com.chozy.server.home.service.RecommendedProductReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CollectorRecommendedProductReader implements RecommendedProductReader {
  private final JdbcTemplate jdbc;
  private final ObjectReader publications;

  public CollectorRecommendedProductReader(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.publications = mapper.readerFor(Publication.class)
        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public Optional<Publication> latest(Source source, int size) {
    try {
      var payload = jdbc.queryForObject("SELECT public.read_supply_home(?, ?)::text", String.class, source.name(), size);
      if (payload == null) return Optional.empty();
      Publication publication = publications.readValue(payload);
      if (publication == null) throw new BusinessException(RECOMMENDATIONS_UNAVAILABLE);
      return Optional.of(publication);
    } catch (DataAccessException | JsonProcessingException ex) {
      throw new BusinessException(RECOMMENDATIONS_UNAVAILABLE);
    }
  }
}
