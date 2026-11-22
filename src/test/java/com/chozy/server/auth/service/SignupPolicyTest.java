package com.chozy.server.auth.service;

import static org.assertj.core.api.Assertions.*;
import com.chozy.server.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SignupPolicyTest {
  @Test void productionRejectsEvenExplicitEnable() {
    var env = new MockEnvironment().withProperty("app.auth.unverified-signup-enabled", "true");
    env.setActiveProfiles("prod", "database");
    assertThatThrownBy(() -> new SignupPolicy(env).requireAllowed()).isInstanceOf(BusinessException.class);
  }
  @Test void defaultIsClosedAndDevelopmentMayOptIn() {
    var env = new MockEnvironment();
    assertThatThrownBy(() -> new SignupPolicy(env).requireAllowed()).isInstanceOf(BusinessException.class);
    env.setActiveProfiles("dbdev");
    env.setProperty("app.auth.unverified-signup-enabled", "true");
    new SignupPolicy(env).requireAllowed();
  }
}
