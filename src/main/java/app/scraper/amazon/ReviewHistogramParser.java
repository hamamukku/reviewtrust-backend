package app.scraper.amazon;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the review histogram (per-star percentage), average rating, and review count
 * from an Amazon product detail page using static HTML parsing via Jsoup.
 */
public final class ReviewHistogramParser {

    public static final class Result {
        public final Map<Integer, Integer> percentageByStar = new LinkedHashMap<>();
        public BigDecimal averageRating;
        public int reviewCount;
    }

    private static final Pattern STAR_RX = Pattern.compile(
            "(?:^|\\s)([1-5])\\s*(?:つ|ツ)?\\s*星|([1-5])\\s*stars?|([1-5])\\s*-star",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PCT_RX = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern WIDTH_RX = Pattern.compile("width\\s*:\\s*(\\d{1,3})%");
    private static final Pattern DECIMAL_RX = Pattern.compile("([0-5](?:[\\.,][0-9])?)");

    private static final String SELECTOR_RESOURCE = "selectors-amazon.properties";
    private static final List<String> DEFAULT_DP_CONTAINER_SELECTORS = List.of(
            "#cm_cr_dp_d_rating_histogram"
    );
    private static final List<String> DEFAULT_DP_ROW_SELECTORS = List.of(
            "ul#histogramTable li",
            "div[data-hook=histogram-table] tr",
            ".a-histogram-row",
            "tr.a-histogram-row"
    );
    private static final List<String> DEFAULT_DP_AVERAGE_SELECTORS = List.of(
            "[data-hook=rating-out-of-text]",
            "i[data-hook=average-star-rating] .a-icon-alt"
    );
    private static final List<String> DEFAULT_DP_TOTAL_SELECTORS = List.of(
            "[data-hook=total-review-count]"
    );
    private static final List<String> DEFAULT_HISTOGRAM_ROW_SELECTORS = List.of(
            "table#histogramTable tr",
            "ul#histogramTable li a",
            "div[data-hook=histogram-table] tr",
            "div[data-hook=rating-bar]",
            ".a-histogram-row",
            "tr.a-histogram-row"
    );
    private static final List<String> DEFAULT_AVERAGE_RATING_SELECTORS = List.of(
            "#acrPopover",
            "span[data-hook=rating-out-of-text]",
            "i[data-hook=average-star-rating]",
            "span[data-asin] i.a-icon-star span"
    );
    private static final List<String> DEFAULT_REVIEW_COUNT_SELECTORS = List.of(
            "#acrCustomerReviewText",
            "#acrCustomerReviewLink #acrCustomerReviewText",
            "span[data-hook=total-review-count]"
    );

    private static final List<String> DP_CONTAINER_SELECTORS =
            loadSelectors("dp.hist.container", DEFAULT_DP_CONTAINER_SELECTORS);
    private static final List<String> DP_ROW_SELECTORS =
            loadSelectors("dp.hist.rows", DEFAULT_DP_ROW_SELECTORS);
    private static final List<String> DP_AVERAGE_SELECTORS =
            loadSelectors("dp.hist.average", DEFAULT_DP_AVERAGE_SELECTORS);
    private static final List<String> DP_TOTAL_SELECTORS =
            loadSelectors("dp.hist.total", DEFAULT_DP_TOTAL_SELECTORS);
    private static final List<String> HISTOGRAM_ROW_SELECTORS =
            loadSelectors("histogram.rows", DEFAULT_HISTOGRAM_ROW_SELECTORS);
    private static final List<String> AVERAGE_RATING_SELECTORS =
            loadSelectors("average.rating", DEFAULT_AVERAGE_RATING_SELECTORS);
    private static final List<String> REVIEW_COUNT_SELECTORS =
            loadSelectors("review.count", DEFAULT_REVIEW_COUNT_SELECTORS);

    private ReviewHistogramParser() {
    }

    public static Result parse(String html) {
        return parse(Jsoup.parse(html));
    }

    public static Result parse(Document document) {
        Optional<Result> inline = parseInlineHistogram(document);
        if (inline.isPresent()) {
            return inline.get();
        }

        Result result = new Result();
        collectHistogram(document, result);
        result.averageRating = extractAverageRating(document, AVERAGE_RATING_SELECTORS).orElse(null);
        result.reviewCount = extractReviewCount(document, REVIEW_COUNT_SELECTORS).orElse(0);
        ensureStarOrder(result.percentageByStar);
        return result;
    }

    private static Optional<Result> parseInlineHistogram(Document document) {
        Element container = selectFirst(document, DP_CONTAINER_SELECTORS);
        if (container == null) {
            return Optional.empty();
        }
        Result result = new Result();
        result.averageRating = extractAverageRating(container, DP_AVERAGE_SELECTORS)
                .or(() -> extractAverageRating(document, DP_AVERAGE_SELECTORS))
                .orElse(null);
        result.reviewCount = extractReviewCount(container, DP_TOTAL_SELECTORS)
                .or(() -> extractReviewCount(document, DP_TOTAL_SELECTORS))
                .orElse(0);
        collectHistogram(container, result, DP_ROW_SELECTORS);
        ensureStarOrder(result.percentageByStar);
        return Optional.of(result);
    }

    private static void collectHistogram(Element root, Result result) {
        collectHistogram(root, result, HISTOGRAM_ROW_SELECTORS);
    }

    private static void collectHistogram(Element root, Result result, List<String> selectors) {
        Elements rows = selectCombined(root, selectors);
        for (Element row : rows) {
            OptionalInt star = extractStar(row);
            OptionalInt pct = extractPercent(row);
            if (star.isPresent() && pct.isPresent()) {
                int s = star.getAsInt();
                if (s >= 1 && s <= 5) {
                    int clamped = Math.max(0, Math.min(100, pct.getAsInt()));
                    result.percentageByStar.put(s, clamped);
                }
            }
        }
    }

    private static Element selectFirst(Element root, List<String> selectors) {
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            Element candidate = root.selectFirst(selector);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static Elements selectCombined(Element root, List<String> selectors) {
        Set<Element> dedup = new LinkedHashSet<>();
        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            dedup.addAll(root.select(selector));
        }
        return new Elements(dedup);
    }

    private static OptionalInt extractStar(Element row) {
        OptionalInt viaData = findStar(row.attr("data-star-rating"));
        if (viaData.isPresent()) {
            return viaData;
        }
        OptionalInt viaAria = findStar(row.attr("aria-label"));
        if (viaAria.isPresent()) {
            return viaAria;
        }
        for (Element child : row.getAllElements()) {
            OptionalInt star = findStar(child.attr("aria-label"));
            if (star.isPresent()) {
                return star;
            }
        }
        return findStar(row.text());
    }

    private static OptionalInt findStar(String text) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher matcher = STAR_RX.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    return OptionalInt.of(Integer.parseInt(group));
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt extractPercent(Element row) {
        Elements meters = row.select(".a-meter, .a-meter-bar, [role=progressbar]");
        for (Element meter : meters) {
            OptionalInt fromNow = parseIntIfDigits(meter.attr("aria-valuenow"));
            if (fromNow.isPresent()) {
                return fromNow;
            }
            OptionalInt fromValue = parseIntIfDigits(meter.attr("value"));
            if (fromValue.isPresent()) {
                return fromValue;
            }
            Matcher widthMatcher = WIDTH_RX.matcher(meter.attr("style"));
            if (widthMatcher.find()) {
                return OptionalInt.of(Integer.parseInt(widthMatcher.group(1)));
            }
        }

        Matcher embeddedWidth = WIDTH_RX.matcher(row.html());
        if (embeddedWidth.find()) {
            return OptionalInt.of(Integer.parseInt(embeddedWidth.group(1)));
        }

        Matcher inlinePct = PCT_RX.matcher(row.text());
        if (inlinePct.find()) {
            return OptionalInt.of(Integer.parseInt(inlinePct.group(1)));
        }

        Elements rightCells = row.select(".a-text-right, td:last-child, span.a-size-base, span.a-size-small");
        for (Element cell : rightCells) {
            Matcher matcher = PCT_RX.matcher(cell.text());
            if (matcher.find()) {
                return OptionalInt.of(Integer.parseInt(matcher.group(1)));
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt parseIntIfDigits(String s) {
        if (s != null && !s.isBlank() && s.chars().allMatch(Character::isDigit)) {
            try {
                return OptionalInt.of(Integer.parseInt(s));
            } catch (NumberFormatException ignore) {
                // Fall back to other extraction paths.
            }
        }
        return OptionalInt.empty();
    }

    private static Optional<BigDecimal> extractAverageRating(Element root, List<String> selectors) {
        for (String selector : selectors) {
            Element element = root.selectFirst(selector);
            if (element == null) {
                continue;
            }
            Optional<BigDecimal> candidate = pickDecimal(element.text());
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> extractReviewCount(Element root, List<String> selectors) {
        for (String selector : selectors) {
            Element element = root.selectFirst(selector);
            if (element == null) {
                continue;
            }
            String digits = element.text().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    return Optional.of(Integer.parseInt(digits));
                } catch (NumberFormatException ignore) {
                    // Try next candidate
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> pickDecimal(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DECIMAL_RX.matcher(text.replace(',', '.'));
        if (matcher.find()) {
            try {
                return Optional.of(new BigDecimal(matcher.group(1)));
            } catch (NumberFormatException ignore) {
                // Ignore and fall through
            }
        }
        return Optional.empty();
    }

    private static void ensureStarOrder(Map<Integer, Integer> percentageByStar) {
        Map<Integer, Integer> ordered = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            int clamped = Math.max(0, Math.min(100, percentageByStar.getOrDefault(star, 0)));
            ordered.put(star, clamped);
        }
        int sum = ordered.values().stream().mapToInt(Integer::intValue).sum();
        if (sum > 0 && sum != 100) {
            int diff = 100 - sum;
            int pivotStar = ordered.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(5);
            int adjusted = Math.max(0, Math.min(100, ordered.get(pivotStar) + diff));
            ordered.put(pivotStar, adjusted);
        }
        percentageByStar.clear();
        percentageByStar.putAll(ordered);
    }

    private static List<String> loadSelectors(String key, List<String> defaults) {
        Properties properties = new Properties();
        try (InputStream in = ReviewHistogramParser.class.getClassLoader()
                .getResourceAsStream(SELECTOR_RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignore) {
            // Ignore problems loading overrides and fall back to defaults.
        }

        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaults;
        }

        String[] tokens = value.split("\\s*,\\s*");
        List<String> merged = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                merged.add(token.trim());
            }
        }
        if (merged.isEmpty()) {
            return defaults;
        }

        List<String> all = new ArrayList<>(merged);
        for (String fallback : defaults) {
            if (!all.contains(fallback)) {
                all.add(fallback);
            }
        }
        return all;
    }
}
