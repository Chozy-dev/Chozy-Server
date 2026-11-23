package com.chozy.server.notification.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.OffsetDateTime;

public record NotificationSettings(boolean inAppEnabled, boolean emailEnabled, PushStatus pushStatus,
    EventTypes eventTypes, Thresholds thresholds,
    @Schema(types = {"string", "null"}, format = "date-time") OffsetDateTime updatedAt) {
  public enum PushStatus { COMING_SOON }

  public record EventTypes(boolean supplyPriceEnabled, boolean scoreChangeEnabled, boolean stockOutEnabled,
      boolean restockEnabled, boolean searchVolumeEnabled) {}

  public record Thresholds(int supplyPriceChangePercent, int scoreChangePoints, int searchVolumeIncreasePercent) {}

  public static final class Patch {
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean emailEnabled;
    @JsonSetter(nulls = Nulls.FAIL) @Valid
    private EventPatch eventTypes;
    @JsonSetter(nulls = Nulls.FAIL) @Valid
    private ThresholdPatch thresholds;

    public Boolean getEmailEnabled() { return emailEnabled; }
    public EventPatch getEventTypes() { return eventTypes; }
    public ThresholdPatch getThresholds() { return thresholds; }

    public boolean hasValues() {
      return emailEnabled != null || (eventTypes != null && eventTypes.hasValues())
          || (thresholds != null && thresholds.hasValues());
    }
  }

  public static final class EventPatch {
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean supplyPriceEnabled;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean scoreChangeEnabled;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean stockOutEnabled;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean restockEnabled;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictBoolean.class)
    private Boolean searchVolumeEnabled;

    public Boolean getSupplyPriceEnabled() { return supplyPriceEnabled; }
    public Boolean getScoreChangeEnabled() { return scoreChangeEnabled; }
    public Boolean getStockOutEnabled() { return stockOutEnabled; }
    public Boolean getRestockEnabled() { return restockEnabled; }
    public Boolean getSearchVolumeEnabled() { return searchVolumeEnabled; }

    boolean hasValues() {
      return supplyPriceEnabled != null || scoreChangeEnabled != null || stockOutEnabled != null
          || restockEnabled != null || searchVolumeEnabled != null;
    }
  }

  public static final class ThresholdPatch {
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictInteger.class) @Min(1) @Max(100)
    private Integer supplyPriceChangePercent;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictInteger.class)
    @Schema(allowableValues = {"5", "10", "15"})
    private Integer scoreChangePoints;
    @JsonSetter(nulls = Nulls.FAIL) @JsonDeserialize(using = StrictInteger.class)
    @Schema(allowableValues = {"30", "50", "100"})
    private Integer searchVolumeIncreasePercent;

    public Integer getSupplyPriceChangePercent() { return supplyPriceChangePercent; }
    public Integer getScoreChangePoints() { return scoreChangePoints; }
    public Integer getSearchVolumeIncreasePercent() { return searchVolumeIncreasePercent; }

    boolean hasValues() {
      return supplyPriceChangePercent != null || scoreChangePoints != null || searchVolumeIncreasePercent != null;
    }
  }

  static final class StrictBoolean extends StdDeserializer<Boolean> {
    StrictBoolean() {
      super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_TRUE) || parser.hasToken(JsonToken.VALUE_FALSE)) {
        return parser.getBooleanValue();
      }
      return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
    }
  }

  static final class StrictInteger extends StdDeserializer<Integer> {
    StrictInteger() {
      super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return parser.getIntValue();
      return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
  }
}
