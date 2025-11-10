package com.hamas.reviewtrust.domain.scraping.service;

import com.hamas.reviewtrust.config.ScrapingProperties;
import com.hamas.reviewtrust.domain.scraping.client.AmazonReviewClient;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser.ReviewItem;
import com.hamas.reviewtrust.domain.scraping.repository.ScrapeJobJdbcRepository;
import com.hamas.reviewtrust.common.repository.ExceptionLogJdbcRepository;  // ★ ここを修正
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScrapingService {
    private static final Logger log = LoggerFactory.getLogger(ScrapingService.class);

    private static final Locale DEFAULT_LOCALE = Locale.JAPAN;
    private static final int DEFAULT_LIMIT_MIN = 1;
    private static final int DEFAULT_LIMIT_MAX = 200;

    private static final List<Pattern> ASIN_PATTERNS = List.of(
            Pattern.compile("/dp/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("/product-reviews/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("[?&]asin=([A-Z0-9]{10})(?:&|$)")
    );

    private final ScrapingProperties props;
    private final AmazonReviewClient client;
    private final AmazonReviewParser parser = new AmazonReviewParser();
    private final ScrapeJobJdbcRepository jobJdbc;
    private final ExceptionLogJdbcRepository exRepo;          // ★ 型を修正

    public ScrapingService(ScrapingProperties props,
                           ScrapeJobJdbcRepository jobJdbc,
                           ExceptionLogJdbcRepository exRepo) {  // ★ コンストラクタ引数を修正
        this.props = props;
        this.client = new AmazonReviewClient();
        this.jobJdbc = jobJdbc;
        this.exRepo = exRepo;
        log.info("[ScrapingService] initialized: enabled={}, headless={}",
                isEnabled(), props != null ? props.isHeadless() : null);
    }

    // ===== 公開API =====

    public List<ReviewItem> scrapeAmazonByUrl(String url, int limit) {
        return scrapeAmazonByUrl(url, localeOrDefault(null), limit);
    }

    public List<ReviewItem> scrapeAmazonByUrl(String url, Locale locale, int limit) {
        if (!isEnabled()) {
            log.warn("[scrapeAmazonByUrl] disabled by config");
            return List.of();
        }
        if (isBlank(url)) {
            log.warn("[scrapeAmazonByUrl] url is blank");
            return List.of();
        }
        final int lim = clampLimit(limit);
        final String norm = normalizeToReviewsUrl(url).orElse(url);

        long t0 = System.nanoTime();
        String html = null;
        try {
            html = client.fetchHtmlByUrl(norm, lim);
        } catch (Throwable t) {
            log.warn("[scrapeAmazonByUrl] fetch error url={} msg={}", norm, t.toString());
        }
        if (isBlank(html)) {
            log.warn("[scrapeAmazonByUrl] fetch empty url={}", norm);
            return List.of();
        }
        String asin = extractAsin(norm).orElse(null);
        Locale loc = localeOrDefault(locale);
        List<ReviewItem> items = parser.parse(html, loc, lim, asin);
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();

        if (items.isEmpty()) {
            log.info("[scrapeAmazonByUrl] parsed=0 url={} in {}ms", norm, ms);
            return List.of();
        }
        if (items.size() > lim) items = items.subList(0, lim);
        log.info("[scrapeAmazonByUrl] parsed={} url={} in {}ms", items.size(), norm, ms);
        return items;
    }

    public List<ReviewItem> scrapeAmazonByAsin(String asin, Locale locale, int limit) {
        if (!isEnabled()) {
            log.warn("[scrapeAmazonByAsin] disabled by config");
            return List.of();
        }
        if (isBlank(asin)) return List.of();
        String url = reviewsUrlFromAsin(asin);
        return scrapeAmazonByUrl(url, locale, limit);
    }

    /** productId(推奨: UUID) or ASIN, または url を受ける互換API */
    public Result rescrape(String productIdOrAsin, String url, int limit) {
        if (!isEnabled()) {
            return Result.failure(productIdOrAsin, url, "scraping disabled");
        }

        UUID pid = tryParseUuid(productIdOrAsin);
        UUID jobId = null;
        try {
            if (pid != null) {
                jobId = jobJdbc.insertQueuedForProduct(pid, Math.max(limit, 1));
                jobJdbc.markRunning(jobId);
            } else {
                log.info("[rescrape] argument is not UUID -> skip job record (pid={})", productIdOrAsin);
            }
        } catch (Throwable t) {
            log.warn("[rescrape] job insert failed: {}", t.toString());
        }

        try {
            String targetUrl = firstNonBlank(url, reviewsUrlFromIdOrAsin(productIdOrAsin));
            if (isBlank(targetUrl)) {
                if (jobId != null) jobJdbc.markFailed(jobId, "E_BAD_REQUEST");
                return Result.failure(productIdOrAsin, null, "no target url");
            }

            var items = scrapeAmazonByUrl(targetUrl, localeOrDefault(null), limit);
            if (items.isEmpty()) {
                if (jobId != null) jobJdbc.markFailed(jobId, "E_EMPTY");
                return Result.failure(productIdOrAsin, targetUrl, "No reviews collected");
            } else {
                if (jobId != null) jobJdbc.markOk(jobId, items.size());
                String asin = (notBlank(productIdOrAsin) && productIdOrAsin.length()==10)
                        ? productIdOrAsin
                        : extractAsin(targetUrl).orElse(null);
                return Result.success(asin, targetUrl, items.size(), "OK");
            }
        } catch (Exception e) {
            if (jobId != null) jobJdbc.markFailed(jobId, "E_SCRAPE_FAILED");
            exRepo.save(jobId, "scrape", "E_SCRAPE_FAILED", e.getMessage(), stackOf(e));  // ★ 呼び出し維持
            return Result.failure(productIdOrAsin, url, "SCRAPE_FAILED: " + e.getMessage());
        }
    }

    /** URL + productId(UUIDあるときだけ記録) */
    public Result rescrapeByUrl(String url, Locale locale, int limit, String productId) {
        if (!isEnabled()) {
            return Result.failure(productId, url, "scraping disabled");
        }
        if (isBlank(url)) return Result.failure(productId, null, "url is blank");

        UUID pid = tryParseUuid(productId);
        UUID jobId = null;
        try {
            if (pid != null) {
                jobId = jobJdbc.insertQueuedForProduct(pid, Math.max(limit, 1));
                jobJdbc.markRunning(jobId);
            }
        } catch (Throwable t) {
            log.warn("[rescrapeByUrl] job insert failed: {}", t.toString());
        }

        String norm = normalizeToReviewsUrl(url).orElse(url);
        try {
            var items = scrapeAmazonByUrl(norm, locale, limit);
            String asin = extractAsin(norm).orElse(null);
            String pidOrAsin = notBlank(productId) ? productId : asin;
            if (items.isEmpty()) {
                if (jobId != null) jobJdbc.markFailed(jobId, "E_EMPTY");
                return Result.failure(pidOrAsin, norm, "No reviews collected");
            } else {
                if (jobId != null) jobJdbc.markOk(jobId, items.size());
                return Result.success(pidOrAsin, norm, items.size(), "OK");
            }
        } catch (Exception e) {
            if (jobId != null) jobJdbc.markFailed(jobId, "E_SCRAPE_FAILED");
            exRepo.save(jobId, "scrape", "E_SCRAPE_FAILED", e.getMessage(), stackOf(e));  // ★ 呼び出し維持
            return Result.failure(productId, norm, "SCRAPE_FAILED: " + e.getMessage());
        }
    }

    // ===== 内部ユーティリティ =====

    private boolean isEnabled() {
        try {
            return props == null || props.isEnabled();
        } catch (Throwable ignore) {
            return true;
        }
    }

    private static int clampLimit(int limit) {
        int lim = limit <= 0 ? DEFAULT_LIMIT_MIN : Math.min(limit, DEFAULT_LIMIT_MAX);
        return Math.max(lim, DEFAULT_LIMIT_MIN);
    }

    private static Optional<String> extractAsin(String url) {
        if (url == null) return Optional.empty();
        for (Pattern p : ASIN_PATTERNS) {
            Matcher m = p.matcher(url);
            if (m.find()) return Optional.ofNullable(m.group(1));
        }
        return Optional.empty();
    }

    private static Optional<String> normalizeToReviewsUrl(String input) {
        if (isBlank(input)) return Optional.empty();
        if (input.startsWith("http") && input.contains("/product-reviews/")) return Optional.of(input);
        String asin = extractAsin(input).orElse(null);
        return asin == null ? Optional.of(input) : Optional.of(reviewsUrlFromAsin(asin));
    }

    private static String reviewsUrlFromAsin(String asin) {
        if (isBlank(asin)) return null;
        return "https://www.amazon.co.jp/product-reviews/" + asin + "/?reviewerType=all_reviews";
    }

    private static String reviewsUrlFromIdOrAsin(String idOrAsin) {
        if (isBlank(idOrAsin)) return null;
        if (idOrAsin.length() == 10 && idOrAsin.chars().allMatch(ch -> Character.isDigit(ch) || Character.isUpperCase(ch))) {
            return reviewsUrlFromAsin(idOrAsin);
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private Locale localeOrDefault(Locale loc) {
        try {
            if (props != null && props.getLocale() != null) {
                return Locale.forLanguageTag(props.getLocale());
            }
        } catch (Throwable ignore) {}
        return loc != null ? loc : DEFAULT_LOCALE;
    }

    private static java.util.UUID tryParseUuid(String s) {
        if (s == null) return null;
        try { return java.util.UUID.fromString(s); } catch (Exception ignore) { return null; }
    }

    private static String stackOf(Throwable t) {
        try (java.io.StringWriter sw = new java.io.StringWriter();
             java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
            t.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ignore) { return t.toString(); }
    }

    // ===== Result DTO =====
    public static class Result {
        private final boolean success;
        private final int count;
        private final String asin;
        private final String url;
        private final String message;

        public Result(boolean success, int count, String asin, String url, String message) {
            this.success = success; this.count = count; this.asin = asin; this.url = url; this.message = message;
        }
        public static Result success(String asin, String url, int count, String message) {
            return new Result(true, count, asin, url, message);
        }
        public static Result failure(String asin, String url, String message) {
            return new Result(false, 0, asin, url, message);
        }
        public boolean isSuccess() { return success; }
        public int getCount() { return count; }
        public String getAsin() { return asin; }
        public String getUrl() { return url; }
        public String getMessage() { return message; }
    }
}
