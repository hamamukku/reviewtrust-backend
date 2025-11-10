package com.hamas.reviewtrust.domain.scraping.parser;

import com.hamas.reviewtrust.domain.scraping.selector.AmazonSelectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Amazon レビュー一覧HTMLを解析し、正規化済みの DTO へ変換。
 * - 星: "5つ星のうち4.0" / "4.0 out of 5 stars" などを 1..5 の int に丸め
 * - 日付: JP/EN 主要パターンを LocalDate に正規化
 * - Helpful: "One person ..." → 1, "12 people ..." → 12
 */
public class AmazonReviewParser {

    /** @param html Review一覧HTML
     *  @param locale 例: Locale.JAPAN / Locale.US
     *  @param limit 最大件数
     *  @param asin 既知なら付与（null可）
     */
    public List<ReviewItem> parse(String html, Locale locale, int limit, String asin) {
        if (html == null || html.isBlank()) return List.of();

        Document doc = Jsoup.parse(html);
        Elements reviewBlocks = doc.select(AmazonSelectors.REVIEW_BLOCK);
        List<ReviewItem> out = new ArrayList<>();

        for (Element r : reviewBlocks) {
            if (limit > 0 && out.size() >= limit) break;

            // ---- reviewId ----
            String reviewId = Optional.ofNullable(r.attr(AmazonSelectors.REVIEW_ID_ATTR))
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> {
                        String id = r.id();
                        if (id != null && id.startsWith("customer_review-")) {
                            return id.substring("customer_review-".length());
                        }
                        return id == null || id.isBlank() ? null : id;
                    });

            // ---- title ----
            String title = firstText(r, AmazonSelectors.TITLE);

            // ---- body ----
            String body = firstText(r, AmazonSelectors.BODY);

            // ---- rating (1..5) ----
            String starsText = firstText(r, AmazonSelectors.STARS);
            int rating = normalizeStars(starsText);

            // ---- date ----
            String dateText = firstText(r, AmazonSelectors.DATE);
            LocalDate reviewDate = normalizeDate(dateText, locale);

            // ---- reviewer ----
            String reviewer = firstText(r, AmazonSelectors.REVIEWER);

            // ---- review URL ----
            String href = null;
            Element tAnchor = r.selectFirst("a[data-hook=review-title], a.review-title");
            if (tAnchor != null) {
                href = tAnchor.attr("href");
                href = absolutizeAmazonHref(href);
            }

            // ---- helpful votes ----
            String helpfulText = firstText(r, AmazonSelectors.HELPFUL);
            int helpful = normalizeHelpful(helpfulText);

            // ---- ASIN ----
            String itemAsin = asin; // 外から来たものを優先
            if (itemAsin == null) {
                // タイトルリンクなどのURLからASINを拾う
                itemAsin = extractAsin(href).orElse(null);
            }

            if (reviewId != null) {
                out.add(new ReviewItem(
                        itemAsin,
                        reviewId,
                        nullToEmpty(title),
                        nullToEmpty(body),
                        rating,
                        reviewDate,
                        nullToEmpty(reviewer),
                        href,
                        helpful
                ));
            }
        }
        return out;
    }

    // ============== Normalizers ==============

    private static int normalizeStars(String s) {
        if (s == null || s.isBlank()) return 0;
        // JP: "5つ星のうち4.0", EN: "4.0 out of 5 stars"
        // 数値を拾って 1..5 の範囲にクリップ
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9])?)").matcher(s);
        if (m.find()) {
            try {
                double f = Double.parseDouble(m.group(1));
                int v = (int)Math.round(f); // 4.0→4, 4.5→5 に丸め
                return Math.max(1, Math.min(5, v));
            } catch (NumberFormatException ignore) {}
        }
        return 0;
    }

    private static LocalDate normalizeDate(String s, Locale locale) {
        if (s == null || s.isBlank()) return null;

        String t = s.trim();

        // JP例: "2023年10月1日に日本でレビュー済み"
        Matcher jp = Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日").matcher(t);
        if (jp.find()) {
            int y = Integer.parseInt(jp.group(1));
            int m = Integer.parseInt(jp.group(2));
            int d = Integer.parseInt(jp.group(3));
            return LocalDate.of(y, m, d);
        }

        // EN例: "Reviewed in Japan on October 1, 2023"
        int idx = t.toLowerCase(locale).lastIndexOf(" on ");
        String datePart = idx >= 0 ? t.substring(idx + 4) : t;
        // "October 1, 2023" / "1 October 2023"
        for (String p : new String[]{"MMMM d, uuuu", "d MMMM uuuu", "MMM d, uuuu", "d MMM uuuu"}) {
            try {
                return LocalDate.parse(datePart.trim(), DateTimeFormatter.ofPattern(p, locale));
            } catch (Exception ignored) {}
        }

        // ISO風
        try {
            return LocalDate.parse(datePart.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {}

        return null;
    }

    private static int normalizeHelpful(String s) {
        if (s == null || s.isBlank()) return 0;
        String t = s.trim();
        if (t.toLowerCase(Locale.ENGLISH).startsWith("one person")) return 1; // EN: One person found this helpful
        // 数字だけ抽出（"12人のお客様..." / "12 people ..."）
        Matcher m = Pattern.compile("(\\d{1,7})").matcher(t.replace(",", ""));
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignore) {}
        }
        return 0;
    }

    private static String firstText(Element scope, String selector) {
        if (scope == null) return null;
        Element el = scope.selectFirst(selector);
        return el == null ? null : el.text();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String absolutizeAmazonHref(String href) {
        if (href == null || href.isBlank()) return null;
        try {
            URI u = new URI(href);
            if (u.isAbsolute()) return href;
            // 相対なら日本ドメインで
            return "https://www.amazon.co.jp" + (href.startsWith("/") ? href : "/" + href);
        } catch (URISyntaxException e) {
            return href;
        }
    }

    private static Optional<String> extractAsin(String url) {
        if (url == null) return Optional.empty();
        for (Pattern p : ASIN_PATTERNS) {
            Matcher m = p.matcher(url);
            if (m.find()) return Optional.ofNullable(m.group(1));
        }
        return Optional.empty();
    }

    private static final List<Pattern> ASIN_PATTERNS = List.of(
        Pattern.compile("/dp/([A-Z0-9]{10})(?:/|\\?|$)"),
        Pattern.compile("/product-reviews/([A-Z0-9]{10})(?:/|\\?|$)"),
        Pattern.compile("[?&]asin=([A-Z0-9]{10})(?:&|$)")
    );

    // ============== DTO ==============

    public static class ReviewItem {
        private final String asin;
        private final String reviewId;
        private final String title;
        private final String body;
        private final int rating;
        private final LocalDate reviewDate;
        private final String reviewer;
        private final String reviewUrl;
        private final int helpfulVotes;

        public ReviewItem(String asin,
                          String reviewId,
                          String title,
                          String body,
                          int rating,
                          LocalDate reviewDate,
                          String reviewer,
                          String reviewUrl,
                          int helpfulVotes) {
            this.asin = asin;
            this.reviewId = reviewId;
            this.title = title;
            this.body = body;
            this.rating = rating;
            this.reviewDate = reviewDate;
            this.reviewer = reviewer;
            this.reviewUrl = reviewUrl;
            this.helpfulVotes = helpfulVotes;
        }

        public String getAsin() { return asin; }
        public String getReviewId() { return reviewId; }
        public String getTitle() { return title; }
        public String getBody() { return body; }
        public int getRating() { return rating; }
        public LocalDate getReviewDate() { return reviewDate; }
        public String getReviewer() { return reviewer; }
        public String getReviewUrl() { return reviewUrl; }
        public int getHelpfulVotes() { return helpfulVotes; }
    }
}
