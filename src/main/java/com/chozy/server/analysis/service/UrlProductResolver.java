package com.chozy.server.analysis.service;

import com.chozy.server.analysis.dto.AnalysisSource;
import com.chozy.server.analysis.dto.UrlAnalyses.Resolution;
import com.chozy.server.analysis.dto.UrlAnalyses.SubmitRequest;
import com.chozy.server.analysis.dto.UrlAnalyses.UrlInput;
import com.chozy.server.analysis.dto.UrlAnalyses.UrlSource;
import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class UrlProductResolver {
  private static final Pattern NUMERIC_PATH = Pattern.compile("/([0-9]{1,100})/?");
  private static final Pattern OWNERCLAN_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");
  private static final String DOMEME_URL = "https://domeme.domeggook.com/";
  private static final String OWNERCLAN_URL = "https://www.ownerclan.com/V2/product/view.php?selfcode=";

  private final ObjectProvider<UrlProductCatalog> catalogs;

  public UrlProductResolver(ObjectProvider<UrlProductCatalog> catalogs) {
    this.catalogs = catalogs;
  }

  public Resolution resolve(String url) {
    return lookup(url).resolution();
  }

  public UrlInput resolveForAnalysis(SubmitRequest request) {
    var result = lookup(request.url());
    var resolution = result.resolution();
    if (!resolution.supported()) throw invalid();

    String optionId = optionalText(request.selectedOptionId());
    if (optionId == null && resolution.optionSelectionRequired()) throw invalid();
    if (optionId != null && resolution.options().stream().noneMatch(option -> option.optionId().equals(optionId))) {
      throw invalid();
    }
    return new UrlInput(resolution.analysisType(), resolution.productId(), resolution.originalUrl(),
        resolution.resolvedUrl(), result.productName(), optionalText(request.keyword()), optionId);
  }

  private Lookup lookup(String originalUrl) {
    URI uri;
    try {
      uri = URI.create(originalUrl.strip());
    } catch (IllegalArgumentException ex) {
      throw invalid();
    }
    if (uri.getHost() == null || uri.getRawUserInfo() != null
        || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))) {
      throw invalid();
    }
    var source = switch (uri.getHost().toLowerCase(Locale.ROOT)) {
      case "domeme.domeggook.com" -> UrlSource.DOMEME;
      case "domeggook.com", "www.domeggook.com" -> UrlSource.DOMEGGOOK;
      case "ownerclan.com", "www.ownerclan.com" -> UrlSource.OWNERCLAN;
      default -> UrlSource.OTHER;
    };
    int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    String id = uri.getPort() == -1 || uri.getPort() == defaultPort ? productId(uri, source) : null;
    if (id == null) return unsupported(source, null, originalUrl, "지원하는 상품 링크를 확인하거나 직접 입력해 주세요.");

    var target = source == UrlSource.OWNERCLAN ? AnalysisSource.OWNERCLAN : AnalysisSource.DOMEME;
    var catalog = catalogs.getIfAvailable();
    if (catalog == null) throw new BusinessException(ErrorCode.URL_ANALYSIS_UNAVAILABLE);
    var product = catalog.find(target, id);
    if (product.isEmpty()) {
      String notice = source == UrlSource.DOMEGGOOK ? "상품번호 " + id + "은 도매매에 위탁 상품으로 없어요."
          : "상품 정보를 찾을 수 없어요. 직접 입력해 주세요.";
      return unsupported(source, id, originalUrl, notice);
    }
    var options = product.get().options();
    boolean selectionRequired = !options.isEmpty()
        && options.stream().anyMatch(option -> option.price().compareTo(options.getFirst().price()) != 0);
    boolean converted = source == UrlSource.DOMEGGOOK;
    String notice = converted ? "도매꾹 링크(상품번호 " + id + ")를 같은 번호의 도매매 상품으로 바꿔 분석합니다." : null;
    String resolvedUrl = (target == AnalysisSource.DOMEME ? DOMEME_URL : OWNERCLAN_URL) + id;
    return new Lookup(new Resolution(source, id, true, target, converted, originalUrl, resolvedUrl, false, notice,
        selectionRequired, options), product.get().name());
  }

  private static String productId(URI uri, UrlSource source) {
    if (source == UrlSource.DOMEME || source == UrlSource.DOMEGGOOK) {
      var match = NUMERIC_PATH.matcher(uri.getRawPath());
      return match.matches() ? match.group(1) : null;
    }
    if (source != UrlSource.OWNERCLAN || !"/V2/product/view.php".equals(uri.getRawPath())
        || uri.getRawQuery() == null) return null;
    var keys = Arrays.stream(uri.getRawQuery().split("&")).filter(query -> query.startsWith("selfcode="))
        .map(query -> query.substring("selfcode=".length())).toList();
    return keys.size() == 1 && OWNERCLAN_KEY.matcher(keys.getFirst()).matches() ? keys.getFirst() : null;
  }

  private static Lookup unsupported(UrlSource source, String id, String url, String notice) {
    return new Lookup(new Resolution(source, id, false, null, false, url, null, true, notice, false, List.of()), null);
  }

  private static String optionalText(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static BusinessException invalid() {
    return new BusinessException(ErrorCode.INVALID_INPUT);
  }

  private record Lookup(Resolution resolution, String productName) {}
}
