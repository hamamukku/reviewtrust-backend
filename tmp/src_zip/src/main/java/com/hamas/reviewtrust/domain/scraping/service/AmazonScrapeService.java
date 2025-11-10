package com.hamas.reviewtrust.domain.scraping.service;

import com.hamas.reviewtrust.domain.scraping.client.AmazonReviewClient;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser.ReviewItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AmazonScrapeService {
    private static final Logger log = LoggerFactory.getLogger(AmazonScrapeService.class);

    private static final Locale DEFAULT_LOCALE = Locale.JAPAN;
    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_TIMEOUT_SECONDS = 35;

    /** Dev 用の軽量API（URLから直でプレビュー） */
    public List<ReviewItem> preview(String url, int limit) {
        return scrapeByUrl(url, limit);
    }

    /** 任意URLからレビュー抽出（dp URLでもASIN抽出→レビュー一覧URLにフォールバック） */
    public List<ReviewItem> scrapeByUrl(String url, int limit) {
        if (url == null || url.isBlank()) {
            return Collections.emptyList();
        }
        int lim = limit > 0 ? limit : DEFAULT_LIMIT;

        String asin = extractAsin(url);
        String html;
        try (AmazonReviewClient client = new AmazonReviewClient()) {
            if (asin != null && !isReviewsUrl(url)) {
                String reviewsUrl = client.buildReviewsUrlFromAsin(asin, DEFAULT_LOCALE);
                html = client.fetchHtmlByUrl(reviewsUrl, DEFAULT_TIMEOUT_SECONDS);
            } else {
                html = client.fetchHtmlByUrl(url, DEFAULT_TIMEOUT_SECONDS);
            }
        } catch (Exception e) {
            log.error("scrapeByUrl failed. url={}", url, e);
            return Collections.emptyList();
        }

        AmazonReviewParser parser = new AmazonReviewParser();
        return parser.parse(html, DEFAULT_LOCALE, lim, asin);
    }

    /** ASIN指定でレビュー抽出 */
    public List<ReviewItem> scrapeByAsin(String asin, Locale locale, int limit) {
        if (asin == null || asin.isBlank()) return Collections.emptyList();
        int lim = limit > 0 ? limit : DEFAULT_LIMIT;
        Locale loc = (locale != null ? locale : DEFAULT_LOCALE);

        String html;
        try (AmazonReviewClient client = new AmazonReviewClient()) {
            html = client.fetchHtmlByAsin(asin, loc, DEFAULT_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.error("scrapeByAsin failed. asin={} locale={}", asin, loc, e);
            return Collections.emptyList();
        }

        AmazonReviewParser parser = new AmazonReviewParser();
        return parser.parse(html, loc, lim, asin);
    }

    // ---------- helpers ----------

    private static boolean isReviewsUrl(String url) {
        try {
            URI u = new URI(url);
            String path = u.getPath() == null ? "" : u.getPath();
            return path.contains("/product-reviews/") || path.contains("/gp/customer-reviews/");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** dp/ASIN, product/ASIN, product-reviews/ASIN を素朴抽出 */
    private static String extractAsin(String url) {
        Pattern p = Pattern.compile("/(?:dp|gp/product|product-reviews)/([A-Z0-9]{8,14})");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
