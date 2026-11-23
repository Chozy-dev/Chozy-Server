package com.chozy.server.common.config;

import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Set;

public final class OpenApiSchemas {
  private OpenApiSchemas() {}

  public static void allowNull(Schema<?> model, String... properties) {
    if (model == null) return;
    var fields = model.getProperties();
    // In OpenAPI 3.1, referenced and enum schemas need a separate branch to accept null.
    for (String property : properties) {
      Schema<?> current = fields.get(property);
      fields.put(property, new Schema<>().anyOf(List.of(current, new Schema<>().types(Set.of("null")))));
    }
  }
}
