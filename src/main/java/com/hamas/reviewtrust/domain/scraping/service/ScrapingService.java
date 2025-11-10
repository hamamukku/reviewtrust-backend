package com.hamas.reviewtrust.domain.scraping.service;

import com.hamas.reviewtrust.common.repository.ExceptionLogJdbcRepository;
import com.hamas.reviewtrust.config.AmazonScrapingProperties;
import com.hamas.reviewtrust.config.ScrapingProperties;
import com.hamas.reviewtrust.domain.reviews.ReviewUpsertRepository;
import com.hamas.reviewtrust.domain.reviews.ReviewUpsertRepository.ReviewUpsertRequest;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.scraping.client.AmazonReviewClient;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser.ReviewItem;
import com.hamas.reviewtrust.domain.scraping.repository.ScrapeJobJdbcRepository;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import com.hamas.reviewtrust.domain.products.service.ProductIntakeService;
import com.hamas.reviewtrust.scraping.AmazonBrowserScraper;
import com.hamas.reviewtrust.scraping.AmazonBrowserScraper.ReviewsResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.microsoft.playwright.Playwright;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates scraping from Amazon, persists reviews via the upsert repository and records job
 * progress. All heavy lifting (Playwright, parsing, upserts) happens here so higher layers can
 * simply trigger a rescrape.
 */
@Service
public class ScrapingService {

    private static final Logger log = LoggerFactory.getLogger(ScrapingService.class);
    private static final Locale DEFAULT_LOCALE = Locale.JAPAN;
    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 200;
    private static final String SOURCE_AMAZON = "AMAZON";
    private static final List<Pattern> ASIN_PATTERNS = List.of(
            Pattern.compile("/dp/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("/product-reviews/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("[?&]asin=([A-Z0-9]{10})(?:&|$)")
    );
    private static final Pattern CAPTCHA_PATTERN = Pattern.compile("captcha", Pattern.CASE_INSENSITIVE);
    private static final Path LAST_HTML_DUMP = Path.of("data", "last.html");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScrapingProperties properties;
    private final AmazonScrapingProperties amazonProperties;
    private final ScrapeJobJdbcRepository jobRepository;
    private final ProductRepository productRepository;
    private final ReviewUpsertRepository reviewUpsertRepository;
    private final ExceptionLogJdbcRepository exceptionRepository;
    private final ProductIntakeService productIntakeService;
    private final AmazonReviewParser parser = new AmazonReviewParser();

    public ScrapingService(ScrapingProperties properties,
                           AmazonScrapingProperties amazonProperties,
                           ScrapeJobJdbcRepository jobRepository,
                           ProductRepository productRepository,
                           ReviewUpsertRepository reviewUpsertRepository,
                           ExceptionLogJdbcRepository exceptionRepository,
                           ProductIntakeService productIntakeService) {
        this.properties = properties;
        this.amazonProperties = amazonProperties;
        this.jobRepository = jobRepository;
        this.productRepository = productRepository;
        this.reviewUpsertRepository = reviewUpsertRepository;
        this.exceptionRepository = exceptionRepository;
        this.productIntakeService = productIntakeService;
    }

    /* ----------------------------------------------------------------------
     * Public API used by controllers/schedulers
     * ---------------------------------------------------------------------- */

    public Result rescrape(String productIdOrAsin, String url, int requestedLimit) {
        if (!isEnabled()) {
            return Result.failure(productIdOrAsin, url, "scraping disabled");
        }

        UUID productId = tryParseUuid(productIdOrAsin);
        if (productId == null) {
            productId = resolveProductIdByAsin(productIdOrAsin);
            if (productId == null) {
                return Result.failure(productIdOrAsin, url, "product id is required for persistence");
            }
        }

        int limit = clampLimit(requestedLimit);
        Locale locale = resolveLocale(null);
        String targetUrl = normaliseToReviewsUrl(Optional.ofNullable(url).orElse(productIdOrAsin)).orElse(url);
        String asin = extractAsin(targetUrl).orElse(null);

        UUID jobId = null;
        try {
            jobId = jobRepository.insertQueued(productId, SOURCE_AMAZON, targetUrl, limit, "system");
            jobRepository.markRunning(jobId);
        } catch (Exception e) {
            log.warn("[scrape] failed to register job for productId={}", productId, e);
        }

        Instant startedAt = Instant.now();
        try (AmazonReviewClient client = new AmazonReviewClient()) {
            ReviewsResult browserResult = null;
            String html = client.fetchHtmlByUrl(targetUrl, limit);
            if (shouldFallbackToBrowser(html)) {
                String asinForBrowser = asin != null ? asin : extractAsin(targetUrl).orElse(null);
                browserResult = fetchWithBrowser(asinForBrowser);
                if (browserResult != null) {
                    html = browserResult.getReviewsHtml();
                } else {
                    html = null;
                }
            }

            boolean fallbackUsed = browserResult != null && browserResult.isFallbackUsed();
            ProductPageSnapshot pageSnapshot = null;
            if (fallbackUsed && browserResult != null) {
                String fallbackHtml = browserResult.getProductPageHtml();
                if (fallbackHtml != null && !fallbackHtml.isBlank()) {
                    try {
                        ProductIntakeService.Result fallbackResult = productIntakeService.registerOrUpdateFromHtml(
                                fallbackHtml,
                                browserResult.getProductPageUrl()
                        );
                        pageSnapshot = fallbackResult.snapshot();
                    } catch (Exception ex) {
                        log.warn("[scrape] Failed to register fallback snapshot asin={} cause={}",
                                asin, ex.toString(), ex);
                    }
                }
            }

            if (html == null || html.isBlank() || isCaptcha(html)) {
                if (fallbackUsed && pageSnapshot != null) {
                    jobRepository.updateProgress(jobId, 0, 0, formatFallbackMessage(pageSnapshot));
                    jobRepository.markOk(jobId, 0, 0);
                    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                    log.info("[scrape] fallback-only snapshot productId={} asin={}", productId, pageSnapshot.getAsin());
                    return Result.success(productId.toString(), targetUrl, 0, 0, durationMs,
                            "FALLBACK_ONLY", true, pageSnapshot);
                }
                dumpHtml(html);
                markFailed(jobId, "E_CAPTCHA", "Empty page or CAPTCHA encountered");
                return Result.failure(productId.toString(), targetUrl, "EMPTY_OR_CAPTCHA");
            }

            List<ReviewItem> items = parser.parse(html, locale, limit, asin);
            if (items.isEmpty()) {
                if (fallbackUsed && pageSnapshot != null) {
                    jobRepository.updateProgress(jobId, 0, 0, formatFallbackMessage(pageSnapshot));
                    jobRepository.markOk(jobId, 0, 0);
                    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
                    log.info("[scrape] fallback snapshot without reviews productId={} asin={}", productId, pageSnapshot.getAsin());
                    return Result.success(productId.toString(), targetUrl, 0, 0, durationMs,
                            "FALLBACK_ONLY", true, pageSnapshot);
                }
                dumpHtml(html);
                markFailed(jobId, "E_EMPTY", "No reviews parsed");
                return Result.failure(productId.toString(), targetUrl, "NO_REVIEWS");
            }

            int collected = items.size();
            int upserted = 0;
            for (ReviewItem item : items) {
                try {
                    reviewUpsertRepository.upsert(toRequest(productId, item));
                    upserted++;
                    updateProgress(jobId, collected, upserted);
                } catch (Exception upsertError) {
                    log.warn("[scrape] upsert failed reviewId={} productId={}", item.getReviewId(), productId, upsertError);
                    exceptionRepository.save(jobId, "scrape", "E_UPSERT", upsertError.getMessage(), stackOf(upsertError));
                }
            }

            if (fallbackUsed && pageSnapshot != null) {
                jobRepository.updateProgress(jobId, collected, upserted, formatFallbackMessage(pageSnapshot));
            }

            jobRepository.markOk(jobId, collected, upserted);
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info("[scrape] completed productId={} collected={} upserted={}", productId, collected, upserted);
            String message = fallbackUsed
                    ? "OK (%d/%d) +FALLBACK".formatted(upserted, collected)
                    : "OK (%d/%d)".formatted(upserted, collected);
            return Result.success(productId.toString(), targetUrl, collected, upserted, durationMs,
                    message, fallbackUsed, pageSnapshot);
        } catch (Exception e) {
            dumpHtml(null);
            markFailed(jobId, "E_SCRAPE_FAILED", e.getMessage());
            exceptionRepository.save(jobId, "scrape", "E_SCRAPE_FAILED", e.getMessage(), stackOf(e));
            return Result.failure(productId.toString(), targetUrl, "SCRAPE_FAILED: " + e.getMessage());
        } finally {
            if (jobId != null) {
                jobRepository.touchFinished(jobId);
            }
            Duration took = Duration.between(startedAt, Instant.now());
            log.debug("[scrape] finished job productId={} in {} ms", productId, took.toMillis());
        }
    }

    /**
     * Lightweight preview API used by the public preview controller. No persistence or job
     * tracking, purely fetch-and-parse.
     */
    public List<ReviewItem> previewByUrl(String url, int limit) {
        int target = clampLimit(limit);
        Locale locale = resolveLocale(null);
        String norm = normaliseToReviewsUrl(url).orElse(url);
        String asin = extractAsin(norm).orElse(null);
        try (AmazonReviewClient client = new AmazonReviewClient()) {
            ReviewsResult browserResult = null;
            String html = client.fetchHtmlByUrl(norm, target);
            if (shouldFallbackToBrowser(html)) {
                browserResult = fetchWithBrowser(asin != null ? asin : extractAsin(norm).orElse(null));
                html = browserResult != null ? browserResult.getReviewsHtml() : null;
            }
            if (html == null || html.isBlank()) {
                if (browserResult != null && browserResult.isFallbackUsed()) {
                    log.debug("[preview] fallback snapshot would be captured asin={}", asin);
                }
                dumpHtml(html);
                return List.of();
            }
            return parser.parse(html, locale, target, asin);
        } catch (Exception e) {
            log.warn("[preview] failed url={}", url, e);
            return List.of();
        }
    }

    public List<ReviewItem> previewByAsin(String asin, Locale locale, int limit) {
        String url = reviewsUrlFromAsin(asin);
        return previewByUrl(url, limit);
    }

    public Result rescrapeByUrl(String url, Locale locale, int limit, String productId) {
        String targetId = productId;
        if (targetId == null || targetId.isBlank()) {
            UUID resolved = resolveProductIdByAsin(url);
            targetId = resolved != null ? resolved.toString() : null;
        }
        if (targetId == null) {
            return Result.failure(null, url, "product id is required for rescrape");
        }
        return rescrape(targetId, url, limit);
    }

    /* ----------------------------------------------------------------------
     * Helpers
     * ---------------------------------------------------------------------- */

    private boolean shouldFallbackToBrowser(String html) {
        if (amazonProperties == null || !amazonProperties.isEnableBrowserLogin()) {
            return false;
        }
        if (html == null) {
            return true;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("ap_login_form")
                || lower.contains("ap_email_login")
                || lower.contains("validatecaptcha")
                || lower.contains("robot check")
                || (lower.contains("signin") && lower.contains("ap_password"));
    }

    private ReviewsResult fetchWithBrowser(String asin) {
        if (asin == null || asin.isBlank() || amazonProperties == null) {
            return null;
        }
        Path statePath = resolveStatePath();
        try (Playwright playwright = Playwright.create()) {
            AmazonBrowserScraper scraper = new AmazonBrowserScraper(
                    playwright,
                    amazonProperties.isHeadless(),
                    statePath
            );
            try {
                return scraper.fetchReviewsHtml(asin, false, statePath);
            } finally {
                scraper.close();
            }
        } catch (Exception ex) {
            log.warn("[scrape] Browser fallback failed asin={} cause={}", asin, ex.toString(), ex);
            return null;
        }
    }

    private Path resolveStatePath() {
        String configured = amazonProperties.getStorageStatePath();
        String candidate = (configured == null || configured.isBlank())
                ? "./var/amazon_state.json"
                : configured;
        try {
            return Paths.get(candidate);
        } catch (Exception ex) {
            log.warn("[scrape] Invalid storageStatePath '{}', falling back to default", candidate, ex);
            return Paths.get("./var/amazon_state.json");
        }
    }

    private ReviewUpsertRequest toRequest(UUID productId, ReviewItem item) {
        return new ReviewUpsertRequest(
                productId,
                SOURCE_AMAZON,
                item.getReviewId(),
                null,
                item.getTitle(),
                item.getBody(),
                item.getRating(),
                item.getReviewDate(),
                item.getReviewer(),
                item.getReviewId(), // reviewer reference defaults to review id
                item.getReviewUrl(),
                item.getHelpfulVotes()
        );
    }

    private void updateProgress(UUID jobId, int collected, int upserted) {
        if (jobId == null) return;
        String message = "processed %d/%d".formatted(upserted, collected);
        jobRepository.updateProgress(jobId, collected, upserted, message);
    }

    private String formatFallbackMessage(ProductPageSnapshot snapshot) {
        if (snapshot == null) {
            return "PRODUCT_PAGE_FALLBACK";
        }
        try {
            ObjectNode root = JSON.createObjectNode();
            String asin = safeTruncate(snapshot.getAsin(), 20);
            if (asin != null) {
                root.put("asin", asin);
            }
            String title = safeTruncate(snapshot.getTitle(), 120);
            if (title != null) {
                root.put("title", title);
            }
            String brand = safeTruncate(snapshot.getBrand(), 80);
            if (brand != null) {
                root.put("brand", brand);
            }
            if (snapshot.getPriceMinor() != null) {
                root.put("priceMinor", snapshot.getPriceMinor());
            }
            if (snapshot.getRatingAverage() != null) {
                root.put("ratingAverage", snapshot.getRatingAverage());
            }
            if (snapshot.getRatingCount() != null) {
                root.put("ratingCount", snapshot.getRatingCount());
            }
            if (!snapshot.getRatingSharePct().isEmpty()) {
                ObjectNode shareNode = JSON.createObjectNode();
                snapshot.getRatingSharePct().forEach((star, pct) -> {
                    if (pct != null) {
                        shareNode.put(String.valueOf(star), pct);
                    }
                });
                root.set("ratingSharePct", shareNode);
            }
            ArrayNode inlineArray = null;
            for (ProductPageSnapshot.InlineReview inlineReview : snapshot.getInlineReviews()) {
                ObjectNode reviewNode = JSON.createObjectNode();
                boolean hasContent = false;
                String reviewTitle = safeTruncate(inlineReview.getTitle(), 120);
                if (reviewTitle != null) {
                    reviewNode.put("title", reviewTitle);
                    hasContent = true;
                }
                String reviewBody = safeTruncate(inlineReview.getBody(), 280);
                if (reviewBody != null) {
                    reviewNode.put("body", reviewBody);
                    hasContent = true;
                }
                if (inlineReview.getStars() != null) {
                    reviewNode.put("stars", inlineReview.getStars());
                    hasContent = true;
                }
                if (inlineReview.getVerified() != null) {
                    reviewNode.put("verified", inlineReview.getVerified());
                    hasContent = true;
                }
                String date = safeTruncate(inlineReview.getDateText(), 60);
                if (date != null) {
                    reviewNode.put("date", date);
                    hasContent = true;
                }
                if (hasContent) {
                    if (inlineArray == null) {
                        inlineArray = root.putArray("inlineReviews");
                    }
                    inlineArray.add(reviewNode);
                }
            }
            root.put("partial", snapshot.isPartial());
            return "PRODUCT_PAGE_FALLBACK " + JSON.writeValueAsString(root);
        } catch (Exception e) {
            return "PRODUCT_PAGE_FALLBACK";
        }
    }

    private String safeTruncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        if (max <= 3) {
            return trimmed.substring(0, max);
        }
        return trimmed.substring(0, max - 3) + "...";
    }

    private boolean isCaptcha(String html) {
        if (html == null) return true;
        return CAPTCHA_PATTERN.matcher(html).find();
    }

    private void dumpHtml(String html) {
        try {
            Files.createDirectories(LAST_HTML_DUMP.getParent());
            String payload = Objects.requireNonNullElse(html, "<!-- empty -->");
            Files.writeString(
                    LAST_HTML_DUMP,
                    payload,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception e) {
            log.debug("[scrape] failed to write last.html dump", e);
        }
    }

    private void markFailed(UUID jobId, String errorCode, String message) {
        if (jobId != null) {
            jobRepository.markFailed(jobId, errorCode, message);
        }
    }

    private boolean isEnabled() {
        try {
            return properties == null || properties.isEnabled();
        } catch (Exception ex) {
            return true;
        }
    }

    private UUID resolveProductIdByAsin(String asinOrRaw) {
        if (asinOrRaw == null) return null;
        String asin = extractAsin(asinOrRaw).orElse(null);
        if (asin == null) return null;
        return productRepository.findByAsin(asin)
                .map(Product::getId)
                .orElse(null);
    }

    private int clampLimit(int limit) {
        int target = Math.max(LIMIT_MIN, limit);
        return Math.min(target, LIMIT_MAX);
    }

    private Locale resolveLocale(Locale override) {
        if (override != null) return override;
        try {
            String tag = properties != null ? properties.getLocale() : null;
            if (tag != null && !tag.isBlank()) {
                return Locale.forLanguageTag(tag);
            }
        } catch (Exception ignore) {
        }
        return DEFAULT_LOCALE;
    }

    private Optional<String> normaliseToReviewsUrl(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        if (input.startsWith("http") && input.contains("/product-reviews/")) {
            return Optional.of(input);
        }
        return extractAsin(input).map(this::reviewsUrlFromAsin);
    }

    private Optional<String> extractAsin(String value) {
        if (value == null) return Optional.empty();
        for (Pattern pattern : ASIN_PATTERNS) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return Optional.ofNullable(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private String reviewsUrlFromAsin(String asin) {
        return "https://www.amazon.co.jp/product-reviews/" + asin + "/?reviewerType=all_reviews&sortBy=recent";
    }

    private UUID tryParseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stackOf(Throwable error) {
        try (var sw = new java.io.StringWriter(); var pw = new java.io.PrintWriter(sw)) {
            error.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            return error.toString();
        }
    }

    /* ----------------------------------------------------------------------
     * Result DTO
     * ---------------------------------------------------------------------- */

    public static class Result {
        private final boolean success;
        private final int collected;
        private final int upserted;
        private final long durationMs;
        private final String asinOrProduct;
        private final String url;
        private final String message;
        private final boolean fallbackUsed;
        private final ProductPageSnapshot productSnapshot;

        private Result(boolean success,
                       int collected,
                       int upserted,
                       long durationMs,
                       String asinOrProduct,
                       String url,
                       String message,
                       boolean fallbackUsed,
                        ProductPageSnapshot productSnapshot) {
            this.success = success;
            this.collected = collected;
            this.upserted = upserted;
            this.durationMs = durationMs;
            this.asinOrProduct = asinOrProduct;
            this.url = url;
            this.message = message;
            this.fallbackUsed = fallbackUsed;
            this.productSnapshot = productSnapshot;
        }

        public static Result success(String asinOrProduct,
                                     String url,
                                     int collected,
                                     int upserted,
                                     long durationMs,
                                     String message) {
            return new Result(true, collected, upserted, durationMs, asinOrProduct, url, message, false, null);
        }

        public static Result success(String asinOrProduct,
                                     String url,
                                     int collected,
                                     int upserted,
                                     long durationMs,
                                     String message,
                                     boolean fallbackUsed,
                                      ProductPageSnapshot productSnapshot) {
            return new Result(true, collected, upserted, durationMs, asinOrProduct, url, message, fallbackUsed, productSnapshot);
        }

        public static Result failure(String asinOrProduct, String url, String message) {
            return new Result(false, 0, 0, 0L, asinOrProduct, url, message, false, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCollected() {
            return collected;
        }

        public int getUpserted() {
            return upserted;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getAsinOrProduct() {
            return asinOrProduct;
        }

        public String getUrl() {
            return url;
        }

        public String getMessage() {
            return message;
        }

        public boolean isFallbackUsed() {
            return fallbackUsed;
        }

        public ProductPageSnapshot getProductSnapshot() {
            return productSnapshot;
        }
    }
}
