package com.chozy.server.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class CoupangMarketPrice {
  private static final BigDecimal TWO = BigDecimal.valueOf(2);
  private static final Pattern SPEC = Pattern.compile(
      "(?i)(\\d[\\d,]*(?:\\.\\d+)?)\\s*(mAh|ml|미리|리터|mm|cm|kg|키로|L|g|m)(?![a-z])");
  private static final Pattern COUNT = Pattern.compile(
      "(?i)(?<![\\d.])([1-9]\\d*)\\s*(개입|개|입|세트|팩|묶음|박스|켤레|족|EA|P)(?![a-z\\p{IsHangul}])"
          + "|(?:mAh|ml|리터|L|g|kg)\\s*[x×*]\\s*([1-9]\\d*)(?![\\d.])(?:\\s*(?:개|EA))?(?!\\s*[a-z])");
  private static final Pattern PROMOTION = Pattern.compile("(?<![\\d.])([1-9]\\d*)\\s*\\+\\s*([1-9]\\d*)(?![\\d.])");

  private CoupangMarketPrice() {}

  static Sample analyze(JsonNode rows, String productName, BigDecimal shipping) {
    var target = specifications(productName);
    var count = quantity(productName);
    var same = new ArrayList<Offer>();
    var unknown = new ArrayList<Offer>();
    var different = new ArrayList<Offer>();
    var seen = new HashSet<String>();
    int bundles = 0;
    for (var row : rows) {
      var name = row.path("productName").asText("");
      var rawPrice = row.path("productPrice");
      if (name.isBlank() || !rawPrice.isNumber() || rawPrice.decimalValue().signum() <= 0
          || !row.path("isRocket").isBoolean()) continue;
      var id = row.path("productId").asText("");
      if (!id.isEmpty() && !seen.add(id)) continue;
      if (count.compareTo(quantity(name)) != 0) {
        bundles++;
        continue;
      }
      boolean rocket = row.path("isRocket").booleanValue();
      boolean adjusted = !rocket && row.path("isFreeShipping").isBoolean() && !row.path("isFreeShipping").booleanValue();
      var price = adjusted ? rawPrice.decimalValue().add(shipping) : rawPrice.decimalValue();
      var offer = new Offer(price, rocket, adjusted);
      var actual = specifications(name);
      boolean conflicting = actual.stream().anyMatch(spec -> !target.contains(spec) && target.stream()
          .anyMatch(expected -> spec.unit().equals(expected.unit())));
      if (conflicting) different.add(offer);
      else if (!target.isEmpty() && actual.containsAll(target)) same.add(offer);
      else unknown.add(offer);
    }
    List<Offer> comparable = !same.isEmpty() ? same : !unknown.isEmpty() ? unknown : different;
    var center = median(prices(comparable));
    var retained = center == null ? List.<Offer>of() : comparable.stream()
        .filter(offer -> offer.price().multiply(TWO).compareTo(center) >= 0
            && offer.price().compareTo(center.multiply(TWO)) <= 0).toList();
    var regular = prices(retained.stream().filter(offer -> !offer.rocket()).toList());
    var rocket = prices(retained.stream().filter(Offer::rocket).toList());
    var used = regular.size() < 3 ? prices(retained) : regular;
    return new Sample(median(used), median(rocket), used, retained.size(), rocket.size(), regular.size(), rows.size(),
        same.size(), bundles, (int) comparable.stream().filter(Offer::adjusted).count(),
        comparable.size() - retained.size(), regular.size() < 3 || same.isEmpty());
  }

  private static List<BigDecimal> prices(List<Offer> rows) {
    return rows.stream().map(Offer::price).sorted().toList();
  }

  private static BigDecimal median(List<BigDecimal> prices) {
    if (prices.isEmpty()) return null;
    int middle = prices.size() / 2;
    return prices.size() % 2 == 1 ? prices.get(middle) : prices.get(middle - 1).add(prices.get(middle)).divide(TWO);
  }

  private static Set<Specification> specifications(String name) {
    var result = new HashSet<Specification>();
    var values = SPEC.matcher(name);
    while (values.find()) {
      var digits = values.group(1).replace(",", "");
      var value = new BigDecimal(digits);
      var unit = values.group(2).toLowerCase(Locale.ROOT);
      var normalized = switch (unit) {
        case "l", "리터" -> new Specification("ml", value.movePointRight(3));
        case "미리" -> new Specification("ml", value);
        case "kg", "키로" -> new Specification("g", value.movePointRight(3));
        case "cm" -> new Specification("mm", value.movePointRight(1));
        case "m" -> new Specification("mm", value.movePointRight(3));
        default -> new Specification(unit, value);
      };
      result.add(new Specification(normalized.unit(), normalized.value().stripTrailingZeros()));
    }
    return result;
  }

  private static BigDecimal quantity(String name) {
    var promotion = PROMOTION.matcher(name);
    if (promotion.find()) return new BigDecimal(promotion.group(1)).add(new BigDecimal(promotion.group(2)));
    var count = COUNT.matcher(name);
    if (!count.find()) return BigDecimal.ONE;
    return new BigDecimal(count.group(1) == null ? count.group(2) : count.group(1));
  }

  private record Specification(String unit, BigDecimal value) {}

  private record Offer(BigDecimal price, boolean rocket, boolean adjusted) {}

  record Sample(BigDecimal marketPrice, BigDecimal rocketMedian, List<BigDecimal> used, int comparisonCount,
      int rocketCount, int regularCount, int searched, int sameSpecification, int excludedBundles,
      int shippingAdjusted, int excludedOutliers, boolean referenceOnly) {
    void describe(ObjectNode result, String query) {
      result.put("marketPrice", marketPrice).put("rocketMedianPrice", rocketMedian)
          .put("marketPriceSampleCount", regularCount).put("marketPriceReferenceOnly", referenceOnly)
          .put("searchQuery", query).put("searchedProductCount", searched)
          .put("sameSpecificationCount", sameSpecification).put("excludedBundleCount", excludedBundles)
          .put("shippingAdjustedCount", shippingAdjusted).put("excludedOutlierCount", excludedOutliers);
      if (marketPrice == null) result.put("notice", "비교 가능한 시장가를 찾지 못했어요. 예상 판매가를 입력해 주세요.");
      else {
        result.put("marketMinPrice", used.getFirst()).put("marketMaxPrice", used.getLast());
        if (referenceOnly) result.put("notice", "가격 표본이 적거나 같은 규격을 확인하기 어려워 참고용으로 제공해요.");
      }
    }
  }
}
