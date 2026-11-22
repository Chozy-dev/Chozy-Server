package com.chozy.server.common.security;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile({"database", "dbdev"})
public class AuthConfiguration {
  @Bean
  JwtTokens jwtTokens(@Value("${JWT_SECRET_BASE64}") String secret,
      @Value("${JWT_ACCESS_SECONDS:3600}") long access,
      @Value("${JWT_REFRESH_SECONDS:1209600}") long refresh) {
    return new JwtTokens(secret, access, refresh, Clock.systemUTC());
  }
  @Bean
  PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
