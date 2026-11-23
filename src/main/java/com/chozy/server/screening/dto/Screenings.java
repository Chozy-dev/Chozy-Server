package com.chozy.server.screening.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

public final class Screenings {
  private Screenings() {}

  public record ScreeningRequest(@NotEmpty @JsonDeserialize(contentUsing = KeywordText.class)
      List<@NotBlank @Size(max = 500) String> keywords) {}

  public record ShoppingRequest(
      @NotBlank @Size(max = 500) @JsonDeserialize(using = KeywordText.class) String keyword,
      @NotBlank @JsonDeserialize(using = KeywordText.class) String categoryCode) {}

  public enum CompetitionLevel {
    LOW(20), MEDIUM(50), HIGH(80);

    private final int score;

    CompetitionLevel(int score) {
      this.score = score;
    }

    public int score() {
      return score;
    }
  }

  public record ScreeningResult(String screeningId, OffsetDateTime analyzedAt, int totalCount, int excludedCount,
      List<Keyword> keywords, @Schema(types = {"string", "null"}) String notice) {}

  public record Keyword(String keyword, int score, long monthlySearchCount, boolean isSearchCountEstimated,
      BigDecimal clickThroughRate, BigDecimal mobileSearchRatio, CompetitionLevel competitionLevel,
      int competitionScore, @Schema(types = {"number", "null"}) BigDecimal averageAdDepth,
      @Schema(types = {"number", "null"}) BigDecimal searchGrowthRate,
      @Schema(types = {"number", "null"}) BigDecimal shoppingGrowthRate,
      @Schema(types = {"string", "null"}) String categoryCode,
      @Schema(types = {"string", "null"}) String categoryName, Quadrant quadrant,
      boolean trendAvailable, Trend searchTrend) {}

  public record RecentScreenings(List<RecentItem> items) {}

  public record RecentItem(String screeningId, String keyword, int score, long monthlySearchCount,
      CompetitionLevel competitionLevel, OffsetDateTime analyzedAt) {}

  public record Categories(List<Category> categories) {}
  public record Category(String categoryCode, String categoryName) {}

  public record ShoppingResult(String keyword, String categoryCode, String categoryName, int score,
      @Schema(types = {"number", "null"}) BigDecimal searchGrowthRate,
      @Schema(types = {"number", "null"}) BigDecimal shoppingGrowthRate, Quadrant quadrant, Trend shoppingTrend) {}

  public enum QuadrantType { GOLDEN_TIME, BUZZ_ONLY, HIDDEN_DEMAND, DECLINING }

  public record Quadrant(QuadrantType type, String label, String description) {}
  public record Trend(List<Daily> last30Days, List<Monthly> last12Months) {}
  public record Daily(LocalDate date, BigDecimal value) {}
  public record Monthly(@Schema(type = "string", example = "2026-10") YearMonth month, BigDecimal value) {}

  static final class KeywordText extends StdDeserializer<String> {
    KeywordText() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
      if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText().strip();
      return (String) context.handleUnexpectedToken(String.class, parser);
    }
  }
}
