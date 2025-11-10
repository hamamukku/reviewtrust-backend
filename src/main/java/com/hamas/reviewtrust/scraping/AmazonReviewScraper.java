package com.hamas.reviewtrust.scraping;

import app.scraper.amazon.ReviewHistogramParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes reviews from a given Amazon product page and reports success/failure.
 */
@Component
public class AmazonReviewScraper {

    private static final Logger log = LoggerFactory.getLogger(AmazonReviewScraper.class);
    private static final int MAX_RETRIES = 2;
    private static final int[] BACKOFF_SEQUENCE_MS = {2000, 5000};
    private static final Pattern STAR_PATTERN = Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)");
    private static final Pattern ASIN_PATTERN = Pattern.compile("/dp/(B0[0-9A-Z]{8})", Pattern.CASE_INSENSITIVE);
    private static final String PRODUCT_TITLE_WAIT_SELECTOR = String.join(", ",
            "h1#title span#productTitle",
            "span#productTitle",
            "span.a-size-large.product-title-word-break",
            "#title span.a-size-large");
    private static final String REVIEW_SECTION_WAIT_SELECTOR = String.join(", ",
            "#cm-cr-dp-review-list div[data-hook='review']",
            "#reviewsMedley div[data-hook='review']",
            "div[data-hook='review']");
    private static final List<String> PRODUCT_TITLE_SELECTORS = List.of(
            "h1#title span#productTitle:visible",
            "span#productTitle:visible",
            "span.a-size-large.product-title-word-break:visible",
            "#title span.a-size-large:visible"
    );
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final String ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7";

    public Result scrapeOne(Page page, String url) {
        if (page == null) {
            log.error("event=PAGE_FAILED reason=no_page url={}", url);
            return Result.failure("no_page");
        }

        String lastReason = "unknown";
        String asin = tryExtractAsinFromUrl(url).orElse(null);
        String productTitle = fetchProductTitle(page, asin);
        int attempts = MAX_RETRIES + 1;
        HistogramSnapshot histogramSnapshot = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Response response = navigateWithRetries(page, url);
                waitForPageStability(page, true);
                if (response != null) {
                    int status = response.status();
                    if (status == 429 || status == 503) {
                        throw new RetryableFailure("http_" + status);
                    }
                    if (status >= 400) {
                        throw new RetryableFailure("http_" + status);
                    }
                }

                if (isCaptcha(page)) {
                    throw new RetryableFailure("captcha");
                }

                histogramSnapshot = captureHistogram(page, url);
                List<ReviewDetail> reviews = parseReviews(page, url);
                if (reviews.isEmpty()) {
                    log.info("event=NO_REVIEWS url={}", url);
                    return Result.success(Collections.emptyList(),
                            histogramSnapshot != null ? histogramSnapshot.histogram() : null,
                            histogramSnapshot != null ? histogramSnapshot.capturedAt() : Instant.now(),
                            productTitle);
                }

                log.info("event=PAGE_SCRAPED url={} reviews={}", url, reviews.size());
                return Result.success(reviews,
                        histogramSnapshot != null ? histogramSnapshot.histogram() : null,
                        histogramSnapshot != null ? histogramSnapshot.capturedAt() : Instant.now(),
                        productTitle);
            } catch (RetryableFailure retryable) {
                lastReason = retryable.getMessage();
                if (attempt <= MAX_RETRIES) {
                    long waitMs = backoffMillis(attempt);
                    log.warn("event=PAGE_RETRY url={} attempt={} reason={} waitMs={}", url, attempt, lastReason, waitMs);
                    page.waitForTimeout(waitMs);
                    continue;
                }
            } catch (PlaywrightException e) {
                lastReason = "playwright_error";
                if (attempt <= MAX_RETRIES) {
                    long waitMs = backoffMillis(attempt);
                    log.warn("event=PAGE_RETRY url={} attempt={} reason={} waitMs={}", url, attempt, e.getMessage(), waitMs);
                    page.waitForTimeout(waitMs);
                    continue;
                }
                log.debug("Playwright error while scraping url={} message={}", url, e.getMessage());
            } catch (Exception e) {
                lastReason = "unexpected_error";
                log.error("event=PAGE_FAILED url={} reason={} message={}", url, lastReason, e.getMessage(), e);
                return Result.failure(lastReason);
            }
            break;
        }

        log.warn("event=PAGE_FAILED url={} reason={} attempts={}", url, lastReason, MAX_RETRIES + 1);
        return Result.failure(lastReason);
    }

    private HistogramSnapshot captureHistogram(Page page, String url) {
        Instant capturedAt = Instant.now();
        try {
            String html = page.content();
            ReviewHistogramParser.Result histogram;
            if (html == null || html.isBlank()) {
                histogram = new ReviewHistogramParser.Result();
            } else {
                Document document = Jsoup.parse(html);
                histogram = ReviewHistogramParser.parse(document);
            }

            if (sumPct(histogram) == 0) {
                Optional<String> popover = tryExtractPopoverUrl(page);
                if (popover.isPresent()) {
                    String raw = popover.get();
                    String resolved = resolve(raw);
                    if (resolved == null) {
                        log.info("event=HISTOGRAM_FALLBACK_POP_URL status=resolve_failed url={} raw={}", url, raw);
                    } else {
                        try {
                            ReviewHistogramParser.Result candidate = ReviewHistogramParser.parse(fetch(page, resolved));
                            if (sumPct(candidate) > 0) {
                                histogram = candidate;
                                log.info("event=HISTOGRAM_FALLBACK_POP_URL status=ok url={} source={}", url, resolved);
                            } else {
                                log.info("event=HISTOGRAM_FALLBACK_POP_URL status=still_zero url={} source={}", url, resolved);
                            }
                        } catch (IOException e) {
                            log.info("event=HISTOGRAM_FALLBACK_POP_ERROR url={} message={}", url, e.getMessage());
                        }
                    }
                } else {
                    log.info("event=HISTOGRAM_FALLBACK_POP_URL status=not_found url={}", url);
                }
            }

            if (sumPct(histogram) == 0) {
                Optional<String> asin = tryExtractAsinFromUrl(url);
                if (asin.isPresent()) {
                    String reviewsUrl = "https://www.amazon.co.jp/product-reviews/" + asin.get() + "?language=ja_JP";
                    try {
                        ReviewHistogramParser.Result candidate = ReviewHistogramParser.parse(fetch(page, reviewsUrl));
                        if (sumPct(candidate) > 0) {
                            histogram = candidate;
                            log.info("event=HISTOGRAM_FALLBACK_PR_URL status=ok url={} source={}", url, reviewsUrl);
                        } else {
                            log.info("event=HISTOGRAM_FALLBACK_PR_URL status=still_zero url={} source={}", url, reviewsUrl);
                        }
                    } catch (IOException e) {
                        log.info("event=HISTOGRAM_FALLBACK_PR_ERROR url={} message={}", url, e.getMessage());
                    }
                } else {
                    log.info("event=HISTOGRAM_FALLBACK_PR_URL status=asin_not_found url={}", url);
                }
            }

            ReviewHistogramParser.Result safeHistogram = histogram != null ? histogram : new ReviewHistogramParser.Result();
            log.info("event=HISTOGRAM_CAPTURED url={} p5={} p4={} p3={} p2={} p1={} avg={} total={}",
                    url,
                    safeHistogram.percentageByStar.getOrDefault(5, 0),
                    safeHistogram.percentageByStar.getOrDefault(4, 0),
                    safeHistogram.percentageByStar.getOrDefault(3, 0),
                    safeHistogram.percentageByStar.getOrDefault(2, 0),
                    safeHistogram.percentageByStar.getOrDefault(1, 0),
                    safeHistogram.averageRating,
                    safeHistogram.reviewCount);
            return new HistogramSnapshot(safeHistogram, capturedAt);
        } catch (Exception e) {
            log.warn("event=HISTOGRAM_CAPTURE_FAILED url={} message={}", url, e.getMessage(), e);
            return new HistogramSnapshot(null, capturedAt);
        }
    }

    private boolean isCaptcha(Page page) {
        try {
            if (page.locator("form[action*='validateCaptcha']").count() > 0) {
                return true;
            }
            if (page.locator("input[name='cvf_captcha_input']").count() > 0) {
                return true;
            }
            String content = page.content();
            return content != null && content.toLowerCase(Locale.ROOT).contains("captcha");
        } catch (PlaywrightException e) {
            log.debug("Failed to detect captcha message={}", e.getMessage());
            return false;
        }
    }

    private long backoffMillis(int attempt) {
        int index = Math.min(attempt - 1, BACKOFF_SEQUENCE_MS.length - 1);
        return BACKOFF_SEQUENCE_MS[index];
    }

    private List<ReviewDetail> parseReviews(Page page, String url) {
        try {
            Locator reviewsLocator = page.locator("li[data-hook='review']");
            int reviewCount = safeCount(reviewsLocator);
            if (reviewCount == 0) {
                return Collections.emptyList();
            }

            List<ReviewDetail> reviews = new ArrayList<>(reviewCount);
            for (int i = 0; i < reviewCount; i++) {
                Locator review = reviewsLocator.nth(i);
                try {
                    String reviewId = coalesce(
                            safeGetAttribute(review, "data-review-id"),
                            safeGetAttribute(review, "id"),
                            "index-" + i
                    );
                    String title = textOrEmpty(review.locator("[data-hook='review-title']"));
                    String starText = textOrEmpty(review.locator("i[data-hook='review-star-rating'], i[data-hook='cmps-review-star-rating']"));
                    double rating = parseStarRating(starText);
                    String dateText = textOrEmpty(review.locator("[data-hook='review-date']"));
                    String author = textOrEmpty(review.locator("[data-hook='review-author'], span.a-profile-name"));

                    String body = extractReviewBody(review, page);
                    reviews.add(new ReviewDetail(reviewId, title, rating, starText, dateText, author, body, body.length()));
                    log.info("event=REVIEW_PARSED index={} reviewId={} rating={}", i, reviewId, rating);
                } catch (PlaywrightException e) {
                    log.warn("event=REVIEW_PARSE_ERROR url={} index={} message={}", url, i, e.getMessage());
                } catch (RuntimeException e) {
                    log.warn("event=REVIEW_PARSE_ERROR url={} index={} message={}", url, i, e.getMessage());
                }
            }
            return reviews;
        } catch (PlaywrightException e) {
            log.warn("event=REVIEW_PARSE_ERROR url={} message={}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractReviewBody(Locator review, Page page) {
        Locator bodyLocator = review.locator("[data-hook='review-body'], div[data-hook='review-collapsed'], span.review-text-content");
        String body = textOrEmpty(bodyLocator);

        Locator expand = review.locator("a.review-read-more, span.review-read-more");
        if (safeCount(expand) > 0 && isVisible(expand.first())) {
            try {
                expand.first().click();
                page.waitForTimeout(250);
                body = textOrEmpty(review.locator("[data-hook='review-body'], span.review-text-content"));
            } catch (PlaywrightException e) {
                log.debug("Failed to expand review body message={}", e.getMessage());
            }
        }

        return body;
    }

    private String textOrEmpty(Locator locator) {
        if (locator == null) {
            return "";
        }
        try {
            if (locator.count() == 0) {
                return "";
            }
            return locator.first().innerText().trim();
        } catch (PlaywrightException e) {
            log.debug("Failed to read text message={}", e.getMessage());
            return "";
        }
    }

    private boolean isVisible(Locator locator) {
        try {
            return locator != null && locator.isVisible();
        } catch (PlaywrightException e) {
            log.debug("Visibility check failed message={}", e.getMessage());
            return false;
        }
    }

    private int safeCount(Locator locator) {
        if (locator == null) {
            return 0;
        }
        try {
            return locator.count();
        } catch (PlaywrightException e) {
            log.debug("Locator count failed message={}", e.getMessage());
            return 0;
        }
    }

    private String safeGetAttribute(Locator locator, String name) {
        if (locator == null) {
            return null;
        }
        try {
            return locator.getAttribute(name);
        } catch (PlaywrightException e) {
            log.debug("Failed to read attribute name={} message={}", name, e.getMessage());
            return null;
        }
    }

    private String coalesce(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int sumPct(ReviewHistogramParser.Result result) {
        if (result == null || result.percentageByStar.isEmpty()) {
            return 0;
        }
        return result.percentageByStar.values().stream().mapToInt(Integer::intValue).sum();
    }

    private Optional<String> tryExtractPopoverUrl(Page page) {
        try {
            String html = page.content();
            if (html == null || html.isBlank()) {
                return Optional.empty();
            }
            Document document = Jsoup.parse(html);
            Element popover = document.selectFirst("#acrPopover");
            if (popover == null) {
                return Optional.empty();
            }
            String data = popover.attr("data-a-popover");
            if (data == null || data.isBlank()) {
                return Optional.empty();
            }
            String unescaped = Parser.unescapeEntities(data, true);
            JsonNode node = JSON.readTree(unescaped);
            JsonNode urlNode = node.get("url");
            if (urlNode != null && !urlNode.asText().isBlank()) {
                return Optional.of(urlNode.asText());
            }
        } catch (Exception e) {
            log.info("event=HISTOGRAM_FALLBACK_POP_PARSE_FAILED message={}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> tryExtractAsinFromUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ASIN_PATTERN.matcher(sourceUrl);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private String fetchProductTitle(Page page, String asin) {
        if (page == null || asin == null || asin.isBlank()) {
            return "";
        }
        String productUrl = "https://www.amazon.co.jp/dp/" + asin;
        try {
            navigateWithRetries(page, productUrl);
            waitForPageStability(page, false);
            if (isCaptcha(page)) {
                return "";
            }
            waitForProductTitle(page);
            String title = locateProductTitle(page);
            return title == null ? "" : title;
        } catch (Exception e) {
            log.info("event=PRODUCT_TITLE_FETCH_FAILED asin={} message={}", asin, e.getMessage());
            return "";
        }
    }

    private void waitForProductTitle(Page page) {
        try {
            page.waitForSelector(
                    PRODUCT_TITLE_WAIT_SELECTOR,
                    new Page.WaitForSelectorOptions().setTimeout(60_000));
        } catch (PlaywrightException e) {
            log.debug("event=PRODUCT_TITLE_WAIT_TIMEOUT message={}", e.getMessage());
        }
    }

    private void waitForReviewSection(Page page) {
        try {
            page.waitForSelector(
                    REVIEW_SECTION_WAIT_SELECTOR,
                    new Page.WaitForSelectorOptions().setTimeout(45_000));
        } catch (PlaywrightException e) {
            log.debug("event=REVIEW_SECTION_WAIT_TIMEOUT message={}", e.getMessage());
        }
    }

    private String locateProductTitle(Page page) {
        for (String selector : PRODUCT_TITLE_SELECTORS) {
            Locator candidate = page.locator(selector).first();
            if (candidate == null) {
                continue;
            }
            try {
                if (!candidate.isVisible()) {
                    continue;
                }
            } catch (PlaywrightException ignored) {
                continue;
            }
            String text = readLocatorText(candidate);
            if (!text.isBlank()) {
                return text;
            }
        }
        return readLocatorText(page.locator("#productTitle").first());
    }

    private Response navigateWithRetries(Page page, String url) {
        PlaywrightException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return page.navigate(url, new Page.NavigateOptions().setTimeout(90_000));
            } catch (PlaywrightException e) {
                lastError = e;
                log.warn("event=PAGE_NAVIGATE_RETRY attempt={} url={} reason={}", attempt, url, e.getMessage());
                page.waitForTimeout(attempt * 3_000L);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private void waitForPageStability(Page page, boolean waitForReviews) {
        if (page == null) {
            return;
        }
        waitForDomContentLoaded(page);
        if (waitForReviews) {
            waitForReviewSection(page);
        }
        waitForBodyDataAsin(page);
        pauseForAsyncContent();
    }

    private void waitForDomContentLoaded(Page page) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(90_000));
        } catch (PlaywrightException e) {
            log.debug("event=DOMCONTENT_WAIT_TIMEOUT message={}", e.getMessage());
        }
    }

    private void waitForBodyDataAsin(Page page) {
        try {
            page.waitForSelector("body[data-asin]",
                    new Page.WaitForSelectorOptions().setTimeout(60_000));
        } catch (PlaywrightException e) {
            log.debug("event=BODY_ASIN_WAIT_TIMEOUT message={}", e.getMessage());
        }
    }

    private void pauseForAsyncContent() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String readLocatorText(Locator locator) {
        if (locator == null) {
            return "";
        }
        try {
            if (locator.count() == 0) {
                return "";
            }
            String text = locator.textContent();
            if (text == null || text.isBlank()) {
                text = locator.innerText();
            }
            if (text == null) {
                return "";
            }
            return text.replace('\u00A0', ' ').trim();
        } catch (PlaywrightException e) {
            return "";
        }
    }

    private String resolve(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("/")) {
            return "https://www.amazon.co.jp" + href;
        }
        return "https://www.amazon.co.jp/" + href;
    }

    private String fetch(Page page, String targetUrl) throws IOException {
        APIRequestContext request = page.context().request();
        APIResponse response = request.get(targetUrl, RequestOptions.create()
                .setHeader("User-Agent", USER_AGENT)
                .setHeader("Accept-Language", ACCEPT_LANGUAGE));
        if (response == null) {
            throw new IOException("HTTP request returned null response");
        }
        try {
            if (!response.ok() || response.status() >= 400) {
                throw new IOException("HTTP " + response.status());
            }
            return response.text();
        } finally {
            response.dispose();
        }
    }

    private double parseStarRating(String starText) {
        if (starText == null || starText.isBlank()) {
            return 0.0;
        }
        String normalized = starText.replace(',', '.');
        Matcher matcher = STAR_PATTERN.matcher(normalized);
        double value = 0.0;
        boolean found = false;
        while (matcher.find()) {
            try {
                value = Double.parseDouble(matcher.group(1));
                found = true;
            } catch (NumberFormatException ignored) {
            }
        }
        return found ? value : 0.0;
    }

    public record Result(boolean success,
                         int reviewCount,
                         String reason,
                         List<ReviewDetail> reviews,
                         ReviewHistogramParser.Result histogram,
                         Instant capturedAt,
                         String productName) {
        public static Result success(List<ReviewDetail> reviews,
                                     ReviewHistogramParser.Result histogram,
                                     Instant capturedAt,
                                     String productName) {
            List<ReviewDetail> copy = reviews == null ? Collections.emptyList() : List.copyOf(reviews);
            return new Result(true, copy.size(), null, copy, histogram, capturedAt, productName);
        }

        public static Result failure(String reason) {
            return new Result(false, 0, reason, Collections.emptyList(), null, null, null);
        }
    }

    public record ReviewDetail(
            String reviewId,
            String title,
            double rating,
            String ratingText,
            String dateText,
            String author,
            String body,
            int bodyLength
    ) {
    }

    private static class RetryableFailure extends Exception {
        RetryableFailure(String message) {
            super(message);
        }
    }

    private record HistogramSnapshot(ReviewHistogramParser.Result histogram, Instant capturedAt) {
    }
}
