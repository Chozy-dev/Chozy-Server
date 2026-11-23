package com.chozy.server.common.config;

import static com.chozy.server.common.error.ErrorCode.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.error.ErrorResponse;
import com.chozy.server.common.response.ApiResponse;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.HandlerMethod;

final class OpenApiResponses implements OperationCustomizer {
  static final String ERROR_SCHEMA = "ErrorResponse";
  static final String EMPTY_SUCCESS_SCHEMA = "ApiResponseVoid";

  @Override
  public Operation customize(Operation operation, HandlerMethod handler) {
    var contract = handler.getMethodAnnotation(OpenApiContract.class);
    if (contract == null) return operation;
    if (contract.optionalBearer()) {
      operation.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList("bearerAuth")));
    }

    var status = handler.getMethodAnnotation(ResponseStatus.class);
    int successStatus = status == null ? 200 : status.code().value();
    var success = operation.getResponses().get(Integer.toString(successStatus));
    success.setDescription(contract.success());
    if (Void.class == ResolvableType.forMethodReturnType(handler.getMethod())
        .as(ApiResponse.class).getGeneric(0).resolve()) {
      success.setContent(new Content()
          .addMediaType(APPLICATION_JSON_VALUE, jsonSchema(EMPTY_SUCCESS_SCHEMA)
              .example(JsonNodeFactory.instance.objectNode()
                  .put("status", successStatus).put("message", contract.success()).putNull("data"))));
    }

    // The JWT filter validates supplied Bearer headers on public operations as well.
    var errors = EnumSet.of(ACCESS_TOKEN_EXPIRED, INVALID_AUTHENTICATION, INTERNAL_ERROR);
    for (var parameter : handler.getMethodParameters()) {
      if (parameter.hasParameterAnnotation(RequestBody.class)) {
        errors.addAll(EnumSet.of(INVALID_INPUT, INVALID_JSON, UNSUPPORTED_MEDIA_TYPE));
        break;
      }
    }
    errors.addAll(List.of(contract.errors()));
    for (ErrorCode error : errors) {
      var response = operation.getResponses().computeIfAbsent(Integer.toString(error.status()), ignored ->
          new io.swagger.v3.oas.models.responses.ApiResponse()
              .description(HttpStatus.valueOf(error.status()).getReasonPhrase()).content(jsonContent(ERROR_SCHEMA)));
      response.getContent().get(APPLICATION_JSON_VALUE).addExamples(error.code(),
          new Example().summary(error.code() + " " + error.message()).value(ErrorResponse.of(error)));
    }
    return operation;
  }

  static Schema<?> errorSchema() {
    return new ObjectSchema()
        .addProperty("message", new StringSchema())
        .addProperty("status", new IntegerSchema())
        .addProperty("errors", new ArraySchema().items(new Schema<>()).maxItems(0))
        .addProperty("code", new StringSchema())
        .required(List.of("message", "status", "errors", "code"));
  }

  static Schema<?> emptySuccessSchema() {
    return new ObjectSchema()
        .addProperty("status", new IntegerSchema())
        .addProperty("message", new StringSchema())
        .addProperty("data", new Schema<>().types(Set.of("null")))
        .required(List.of("status", "message", "data"));
  }

  private static Content jsonContent(String schema) {
    return new Content().addMediaType(APPLICATION_JSON_VALUE, jsonSchema(schema));
  }

  private static MediaType jsonSchema(String schema) {
    return new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schema));
  }
}
