package com.chozy.server.common.security;

import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.member.service.MemberAccess;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, SecurityProperties properties, SecurityErrorWriter errors,
      ObjectProvider<JwtTokens> tokens, ObjectProvider<MemberAccess> members)
      throws Exception {
    http.addFilterBefore(new JwtAuthenticationFilter(tokens, members, errors),
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .exceptionHandling(
            handler ->
                handler
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            errors.write(response, ErrorCode.INVALID_AUTHENTICATION))
                    .accessDeniedHandler(
                        (request, response, ex) -> errors.write(response, ErrorCode.FORBIDDEN)))
        .authorizeHttpRequests(
            auth -> {
              if (properties.docsPublic())
                auth.requestMatchers(
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll();
              auth.requestMatchers(org.springframework.http.HttpMethod.POST,
                      "/auth/signup", "/auth/login", "/auth/reissue", "/search/screenings",
                      "/search/screenings/{screeningId}/shopping-analysis", "/search/products/resolve-url").permitAll();
              auth.requestMatchers(org.springframework.http.HttpMethod.GET,
                      "/auth/check-login-id", "/search/categories", "/search/coupang-categories",
                      "/home/products/recommended", "/home/keywords/coupang", "/home/keywords/naver",
                      "/home/keywords/compare").permitAll();
              auth.anyRequest().authenticated();
            });
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    cors.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}
