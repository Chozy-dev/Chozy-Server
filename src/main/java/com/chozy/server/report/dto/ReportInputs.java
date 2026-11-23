package com.chozy.server.report.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ReportInputs(long reportId, UpdatedInputs updatedInputs, RecalculatedSummary summary,
    OffsetDateTime updatedAt) {
  public record UpdatedInputs(
      @Schema(types = {"number", "null"}) BigDecimal expectedSellingPrice,
      @Schema(types = {"string", "null"}) String coupangCategoryCode,
      @Schema(types = {"string", "null"}) String keyword,
      @Schema(types = {"string", "null"}) String selectedOptionId,
      @Schema(types = {"number", "null"}) BigDecimal cpc) {}

  public record RecalculatedSummary(
      ReportSummary.ScoreStatus scoreStatus,
      @Schema(types = {"number", "null"}) BigDecimal score,
      ReportSummary.InvestmentGrade investmentGrade,
      @Schema(types = {"string", "null"}) String gradeReason,
      @Schema(types = {"number", "null"}) BigDecimal supplyConditionScore,
      @Schema(types = {"number", "null"}) BigDecimal marketConditionScore,
      List<String> requiredInputs) {
    public static RecalculatedSummary from(ReportSummary summary) {
      return new RecalculatedSummary(summary.scoreStatus(), summary.score(), summary.investmentGrade(),
          summary.gradeReason(), summary.supplyConditionScore(), summary.marketConditionScore(), summary.requiredInputs());
    }
  }

  public static final class ReportInputPatch {
    @DecimalMin("100") @Digits(integer = 16, fraction = 4)
    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = StrictDecimal.class)
    private BigDecimal expectedSellingPrice;
    @Size(min = 1, max = 100)
    private String coupangCategoryCode;
    @Size(min = 1, max = 500)
    private String keyword;
    @Size(min = 1, max = 200)
    private String selectedOptionId;
    @DecimalMin("0") @Digits(integer = 16, fraction = 4)
    @JsonDeserialize(using = StrictDecimal.class)
    private BigDecimal cpc;
    private boolean cpcProvided;

    public BigDecimal getExpectedSellingPrice() { return expectedSellingPrice; }
    public String getCoupangCategoryCode() { return coupangCategoryCode; }
    public String getKeyword() { return keyword; }
    public String getSelectedOptionId() { return selectedOptionId; }

    @Schema(types = {"number", "null"}, description = "명시적으로 null을 보내면 저장된 CPC를 초기화합니다.")
    public BigDecimal getCpc() { return cpc; }

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = StrictText.class)
    public void setCoupangCategoryCode(String value) { coupangCategoryCode = value.strip(); }

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = StrictText.class)
    public void setKeyword(String value) { keyword = value.strip(); }

    @JsonSetter(nulls = Nulls.FAIL)
    @JsonDeserialize(using = StrictText.class)
    public void setSelectedOptionId(String value) { selectedOptionId = value.strip(); }

    @JsonSetter(nulls = Nulls.SET)
    public void setCpc(BigDecimal value) {
      cpc = value;
      cpcProvided = true;
    }

    public boolean hasCpc() { return cpcProvided; }

    public boolean hasInputs() {
      return expectedSellingPrice != null || coupangCategoryCode != null || keyword != null || selectedOptionId != null
          || cpcProvided;
    }
  }

  static final class StrictDecimal extends StdDeserializer<BigDecimal> {
    StrictDecimal() { super(BigDecimal.class); }

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.currentToken().isNumeric()) return parser.getDecimalValue();
      return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
    }
  }

  static final class StrictText extends StdDeserializer<String> {
    StrictText() { super(String.class); }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText();
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
  }
}
