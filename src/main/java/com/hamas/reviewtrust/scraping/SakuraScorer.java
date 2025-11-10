package com.hamas.reviewtrust.scraping;

import java.util.Locale;
import java.util.Map;

/**
 * Heuristic scoring helper to flag suspicious (sakura) reviews.
 */
public class SakuraScorer {

    public static class ProductStats {
        public final Map<String, Integer> bodyHashCounts;
        public final Map<String, Integer> authorCounts;
        public final Map<Integer, Integer> histogram;
        public final int totalReviews;

        public ProductStats(Map<String, Integer> bodyHashCounts,
                            Map<String, Integer> authorCounts,
                            Map<Integer, Integer> histogram,
                            int totalReviews) {
            this.bodyHashCounts = bodyHashCounts;
            this.authorCounts = authorCounts;
            this.histogram = histogram;
            this.totalReviews = totalReviews;
        }
    }

    private static double norm(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double dupScore(String bodyHash, ProductStats stats) {
        if (bodyHash == null || bodyHash.isEmpty()) {
            return 0.0;
        }
        int dup = stats.bodyHashCounts.getOrDefault(bodyHash, 0);
        if (dup <= 1) {
            return 0.0;
        }
        double denom = Math.max(1.0, stats.totalReviews / 10.0);
        return norm((dup - 1) / denom);
    }

    private static double authorScore(String author, ProductStats stats) {
        if (author == null || author.isBlank()) {
            return 0.0;
        }
        int count = stats.authorCounts.getOrDefault(author, 0);
        if (count <= 1) {
            return 0.0;
        }
        return Math.min(1.0, (count - 1) / 5.0);
    }

    private static double starBiasScore(ProductStats stats) {
        if (stats.totalReviews <= 0) {
            return 0.0;
        }
        double total = Math.max(1.0, stats.totalReviews);
        double p5 = stats.histogram.getOrDefault(5, 0) / total;
        double p5Ref = 0.40; // tunable baseline
        if (p5 <= p5Ref) {
            return 0.0;
        }
        return norm((p5 - p5Ref) / (1.0 - p5Ref));
    }

    private static double shortTextScore(int length) {
        if (length <= 0) {
            return 0.0;
        }
        if (length <= 20) {
            return 1.0;
        }
        if (length <= 60) {
            return 0.4;
        }
        return 0.0;
    }

    private static double mismatchScore(String body, int rating) {
        if (body == null || body.isBlank()) {
            return 0.0;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        boolean negative = lower.contains("ダメ") || lower.contains("良くない")
                || lower.contains("bad") || lower.contains("not")
                || lower.contains("嫌");
        return (negative && rating >= 4) ? 1.0 : 0.0;
    }

    public static class ScoreResult {
        public final double score;
        public final String reasons;
        public final boolean flag;

        public ScoreResult(double score, String reasons, boolean flag) {
            this.score = score;
            this.reasons = reasons;
            this.flag = flag;
        }
    }

    public static ScoreResult score(String bodyHash,
                                    String author,
                                    String body,
                                    int bodyLength,
                                    int rating,
                                    ProductStats stats) {
        if (stats == null) {
            return new ScoreResult(0.0, "", false);
        }
        double sDup = dupScore(bodyHash, stats);
        double sAuthor = authorScore(author, stats);
        double sBias = starBiasScore(stats);
        double sShort = shortTextScore(bodyLength);
        double sMismatch = mismatchScore(body, rating);

        double combined = 0.35 * sDup
                + 0.25 * sBias
                + 0.15 * sAuthor
                + 0.15 * sShort
                + 0.10 * sMismatch;
        combined = norm(combined);

        StringBuilder sb = new StringBuilder();
        if (sDup > 0.05) {
            sb.append(String.format(Locale.US, "dup%.2f,", sDup));
        }
        if (sAuthor > 0.05) {
            sb.append(String.format(Locale.US, "author%.2f,", sAuthor));
        }
        if (sBias > 0.05) {
            sb.append(String.format(Locale.US, "bias%.2f,", sBias));
        }
        if (sShort > 0.05) {
            sb.append(String.format(Locale.US, "short%.2f,", sShort));
        }
        if (sMismatch > 0.05) {
            sb.append(String.format(Locale.US, "mismatch%.2f,", sMismatch));
        }
        String reasons = sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";

        boolean flag = combined >= 0.60;
        return new ScoreResult(combined, reasons, flag);
    }
}

