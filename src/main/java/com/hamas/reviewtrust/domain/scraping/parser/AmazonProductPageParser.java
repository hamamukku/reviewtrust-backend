package com.hamas.reviewtrust.domain.scraping.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonProductPageParser {

    private static final Logger log = LoggerFactory.getLogger(AmazonProductPageParser.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String[] PRICE_SELECTORS = {
            "#corePriceDisplay_desktop_feature_div .a-offscreen",
            "#corePriceDisplay_desktop_feature_div span[data-a-color=\"price\"] span.a-offscreen",
            "#priceblock_ourprice",
            "#priceblock_dealprice"
    };

    private static final String[] IMAGE_SELECTORS = {
            "#imgTagWrapperId img#landingImage",
            "#imageBlock_feature_div img",
            "#main-image-container img"
    };

    private static final String[] FEATURE_BULLET_SELECTORS = {
            "#feature-bullets ul.a-unordered-list li span.a-list-item",
            "#feature-bullets ul li"
    };

    private static final String[] INLINE_REVIEW_SELECTORS = {
            "#cm-cr-dp-review-list div[data-hook=\"review\"]",
            "#reviewsMedley div[data-hook=\"review\"]"
    };

    private static final Pattern ASIN_IN_TEXT = Pattern.compile("ASIN\\s*[:：]\\s*([A-Z0-9]{10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAR_FROM_TEXT = Pattern.compile("([\\d])[^\\d]{0,4}星");

    public ProductPageSnapshot parse(String html) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("html is required");
        }
        Document document = Jsoup.parse(html);
        ProductPageSnapshot.Builder builder = ProductPageSnapshot.builder()
                .capturedAt(Instant.now());

        String asin = extractAsin(document);
        builder.asin(asin);

        String title = textOrNull(document.selectFirst("#productTitle"));
        builder.title(title);

        String brand = textOrNull(document.selectFirst("#bylineInfo"));
        if (brand != null) {
            brand = brand.replaceFirst("(?i)^ブランド:\\s*", "").trim();
        }
        builder.brand(brand);

        Long priceMinor = extractPrice(document);
        builder.priceMinor(priceMinor);

        Double ratingAverage = extractRatingAverage(document);
        builder.ratingAverage(ratingAverage);

        Long ratingCount = extractRatingCount(document);
        builder.ratingCount(ratingCount);

        Map<Integer, Double> ratingSharePct = extractRatingShare(document);
        builder.ratingSharePct(ratingSharePct);

        List<String> images = extractImages(document);
        builder.imageUrls(images);

        List<String> featureBullets = extractFeatureBullets(document);
        builder.featureBullets(featureBullets);

        List<ProductPageSnapshot.InlineReview> inlineReviews = extractInlineReviews(document);
        builder.inlineReviews(inlineReviews);

        boolean partial = false;
        if (asin == null || title == null || priceMinor == null) {
            partial = true;
        }
        if (ratingAverage == null || ratingCount == null) {
            partial = true;
        }
        if (ratingSharePct.size() < 5) {
            partial = true;
        }
        if (inlineReviews.isEmpty()) {
            partial = true;
        }
        builder.partial(partial);

        ProductPageSnapshot snapshot = builder.build();
        log.debug("Parsed product snapshot asin={} title='{}' partial={}",
                snapshot.getAsin(), snapshot.getTitle(), snapshot.isPartial());
        return snapshot;
    }

    private String extractAsin(Document document) {
        Element input = document.selectFirst("input#ASIN[value]");
        if (input != null) {
            String value = input.attr("value");
            if (value != null && value.length() == 10) {
                return value.trim();
            }
        }
        for (Element li : document.select("#detailBullets_feature_div li")) {
            String text = textOrNull(li);
            if (text == null) continue;
            Matcher matcher = ASIN_IN_TEXT.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        for (Element row : document.select("#productDetails_detailBullets_sections1 tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th == null || td == null) continue;
            String heading = th.text();
            if (heading != null && heading.toLowerCase(Locale.ROOT).contains("asin")) {
                String value = td.text();
                if (value != null) {
                    value = value.trim();
                    if (value.length() == 10) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private Long extractPrice(Document document) {
        for (String selector : PRICE_SELECTORS) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                Long parsed = TextNormalizer.parseYenToMinor(element.text());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private Double extractRatingAverage(Document document) {
        Element acr = document.selectFirst("#acrPopover");
        if (acr != null && acr.hasAttr("title")) {
            Double parsed = TextNormalizer.parseRating(acr.attr("title"));
            if (parsed != null) {
                return parsed;
            }
        }
        Element text = document.selectFirst("span[data-hook=rating-out-of-text]");
        if (text != null) {
            return TextNormalizer.parseRating(text.text());
        }
        return null;
    }

    private Long extractRatingCount(Document document) {
        Element element = document.selectFirst("#acrCustomerReviewText");
        if (element == null) {
            element = document.selectFirst("[data-hook=total-review-count]");
        }
        if (element == null) {
            element = document.selectFirst("#acrCustomerReviewLink span");
        }
        return element != null ? TextNormalizer.parseCount(element.text()) : null;
    }

    private Map<Integer, Double> extractRatingShare(Document document) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        Elements rows = document.select("#histogramTable li");
        for (Element row : rows) {
            Element anchor = row.selectFirst("a");
            Integer star = null;
            if (anchor != null) {
                star = starFromHref(anchor.attr("href"));
                if (star == null) {
                    star = starFromText(anchor.attr("aria-label"));
                }
            }
            if (star == null) {
                star = starFromText(row.text());
            }
            if (star == null) continue;

            Double percent = null;
            Element meter = row.selectFirst(".a-meter");
            if (meter != null && meter.hasAttr("aria-valuenow")) {
                percent = TextNormalizer.parsePercent(meter.attr("aria-valuenow"));
            }
            if (percent == null) {
                Element percentSpan = row.selectFirst(".a-text-right span");
                if (percentSpan != null) {
                    percent = TextNormalizer.parsePercent(percentSpan.text());
                }
            }
            if (percent == null && anchor != null && anchor.hasAttr("aria-label")) {
                percent = TextNormalizer.parsePercent(anchor.attr("aria-label"));
            }
            if (percent != null) {
                result.put(star, percent);
            }
        }
        for (int star = 5; star >= 1; star--) {
            result.putIfAbsent(star, 0.0d);
        }
        return result;
    }

    private Integer starFromHref(String href) {
        if (href == null) {
            return null;
        }
        if (href.contains("five_star")) return 5;
        if (href.contains("four_star")) return 4;
        if (href.contains("three_star")) return 3;
        if (href.contains("two_star")) return 2;
        if (href.contains("one_star")) return 1;
        return null;
    }

    private Integer starFromText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = TextNormalizer.normalizeDigits(text);
        if (normalized == null) {
            return null;
        }
        Matcher matcher = STAR_FROM_TEXT.matcher(normalized);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private List<String> extractImages(Document document) {
        List<String> urls = new ArrayList<>();
        for (String selector : IMAGE_SELECTORS) {
            Element element = document.selectFirst(selector);
            if (element == null) {
                continue;
            }
            maybeAdd(urls, element.attr("data-old-hires"));
            maybeAdd(urls, element.attr("data-src"));
            maybeAdd(urls, element.attr("src"));
            maybeAddDynamicImages(urls, element.attr("data-a-dynamic-image"));
        }
        return urls;
    }

    private void maybeAddDynamicImages(List<String> urls, String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return;
        }
        try {
            JsonNode node = JSON.readTree(jsonString);
            if (node.isObject()) {
                node.fieldNames().forEachRemaining(url -> maybeAdd(urls, url));
            }
        } catch (IOException e) {
            log.debug("Failed to parse dynamic image JSON: {}", e.getMessage());
        }
    }

    private List<String> extractFeatureBullets(Document document) {
        List<String> bullets = new ArrayList<>();
        for (String selector : FEATURE_BULLET_SELECTORS) {
            Elements elements = document.select(selector);
            for (Element element : elements) {
                String text = textOrNull(element);
                if (text != null && !text.isBlank()) {
                    bullets.add(text);
                }
            }
            if (!bullets.isEmpty()) {
                break;
            }
        }
        return bullets;
    }

    private List<ProductPageSnapshot.InlineReview> extractInlineReviews(Document document) {
        List<ProductPageSnapshot.InlineReview> reviews = new ArrayList<>();
        for (String selector : INLINE_REVIEW_SELECTORS) {
            Elements blocks = document.select(selector);
            if (blocks.isEmpty()) {
                continue;
            }
            for (Element block : blocks) {
                String title = textOrNull(block.selectFirst("[data-hook=review-title] span"));
                if (title == null) {
                    title = textOrNull(block.selectFirst("[data-hook=review-title]"));
                }
                String body = textOrNull(block.selectFirst("[data-hook=review-body] span"));
                if (body == null) {
                    body = textOrNull(block.selectFirst("[data-hook=review-body]"));
                }
                Double ratingValue = null;
                Element ratingElement = block.selectFirst("[data-hook=review-star-rating] span");
                if (ratingElement == null) {
                    ratingElement = block.selectFirst("[data-hook=review-star-rating]");
                }
                if (ratingElement != null) {
                    ratingValue = TextNormalizer.parseRating(ratingElement.text());
                }
                Integer stars = ratingValue != null ? clampStars((int) Math.round(ratingValue)) : null;

                Element verifiedElement = block.selectFirst("[data-hook=avp-badge]");
                if (verifiedElement == null) {
                    verifiedElement = block.selectFirst("span:matches((?i)Amazon.*購入)");
                }
                Boolean verified = verifiedElement != null ? Boolean.TRUE : null;

                String dateText = textOrNull(block.selectFirst("[data-hook=review-date]"));

                if (title == null && body == null && stars == null && dateText == null) {
                    continue;
                }

                reviews.add(new ProductPageSnapshot.InlineReview(
                        title,
                        body,
                        stars,
                        verified,
                        dateText
                ));
            }
            if (!reviews.isEmpty()) {
                break;
            }
        }
        return reviews;
    }

    private Integer clampStars(int value) {
        if (value < 1) return 1;
        if (value > 5) return 5;
        return value;
    }

    private String textOrNull(Element element) {
        return element != null ? element.text().trim() : null;
    }

    private void maybeAdd(List<String> urls, String candidate) {
        if (candidate == null) {
            return;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!urls.contains(trimmed)) {
            urls.add(trimmed);
        }
    }
}
