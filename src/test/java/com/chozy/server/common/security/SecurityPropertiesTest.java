package com.chozy.server.common.security;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {
  @Test
  void rejectWildcardAndNonOrigins() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (String origin :
          List.of(
              "*",
              "https://*.example.com",
              "https://example.com/path",
              "https://user:pass@example.com"))
        assertThat(validator.validate(new SecurityProperties(false, List.of(origin)))).isNotEmpty();
      assertThat(
              validator.validate(new SecurityProperties(false, List.of("http://localhost:3000"))))
          .isEmpty();
    }
  }
}
