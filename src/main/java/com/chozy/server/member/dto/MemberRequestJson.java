package com.chozy.server.member.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

final class MemberRequestJson {
  private MemberRequestJson() {}

  static final class Text extends StdDeserializer<String> {
    Text() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText();
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
  }

  static final class Bool extends StdDeserializer<Boolean> {
    Bool() {
      super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_TRUE) || parser.hasToken(JsonToken.VALUE_FALSE))
        return parser.getBooleanValue();
      return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
    }
  }
}
