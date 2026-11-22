package com.chozy.server.auth.service;

import com.chozy.server.common.error.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class SignupPolicy {
  private final Environment environment;
  public SignupPolicy(Environment environment) { this.environment = environment; }
  public void requireAllowed() {
    if (environment.acceptsProfiles(Profiles.of("prod"))
        || !environment.getProperty("app.auth.unverified-signup-enabled", Boolean.class, false))
      throw new BusinessException(ErrorCode.FORBIDDEN);
  }
}
