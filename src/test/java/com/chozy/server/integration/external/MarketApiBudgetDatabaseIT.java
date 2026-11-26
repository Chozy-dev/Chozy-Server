package com.chozy.server.integration.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

class MarketApiBudgetDatabaseIT {
  @Test
  void requiresExecutableBlockFunctionBeforeReservingOrCallingProvider() {
    try (var db = new PostgreSQLContainer<>("postgres:16").withPassword(UUID.randomUUID().toString())) {
      db.start();
      var admin = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
      String password = UUID.randomUUID().toString();
      admin.execute("CREATE ROLE budget_client LOGIN PASSWORD '" + password + "'");
      admin.execute("CREATE SEQUENCE public.reservations");
      admin.execute("""
          CREATE FUNCTION public.reserve_api_call(text, text, boolean) RETURNS text
          LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog
          AS $$SELECT CASE WHEN nextval('public.reservations') > 0 THEN 'reserved' END$$
          """);
      admin.execute("REVOKE EXECUTE ON FUNCTION public.reserve_api_call(text,text,boolean) FROM PUBLIC");
      admin.execute("GRANT EXECUTE ON FUNCTION public.reserve_api_call(text,text,boolean) TO budget_client");

      var budget = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), "budget_client", password));
      var http = RestClient.builder();
      var provider = MockRestServiceServer.bindTo(http).build();
      var client = new MarketApiClient(http.build(), new ObjectMapper(), budget, Clock.systemUTC(), "coupang", "naver",
          "access", "secret", "ad-key", "ad-secret", "customer", "hub-id", "hub-key");

      assertThatThrownBy(() -> client.keywords("lantern"))
          .isInstanceOf(IllegalStateException.class).hasMessage("Market call budget unavailable");
      admin.execute("""
          CREATE FUNCTION public.block_api_account(text, text) RETURNS text
          LANGUAGE sql AS $$SELECT 'blocked'::text$$
          """);
      admin.execute("REVOKE EXECUTE ON FUNCTION public.block_api_account(text,text) FROM PUBLIC");
      assertThatThrownBy(() -> client.keywords("lantern"))
          .isInstanceOf(IllegalStateException.class).hasMessage("Market call budget unavailable");
      assertThat(admin.queryForObject("SELECT is_called FROM public.reservations", Boolean.class)).isFalse();
      provider.verify();

      admin.execute("GRANT EXECUTE ON FUNCTION public.block_api_account(text,text) TO budget_client");
      provider.reset();
      provider.expect(requestTo("https://api.searchad.naver.com/keywordstool"
              + "?hintKeywords=lantern&includeHintKeywords=1&showDetail=1"))
          .andRespond(withSuccess("{\"keywordList\":[]}", MediaType.APPLICATION_JSON));
      assertThat(client.keywords("lantern").isArray()).isTrue();
      assertThat(admin.queryForObject("SELECT is_called FROM public.reservations", Boolean.class)).isTrue();
      provider.verify();
    }
  }
}
