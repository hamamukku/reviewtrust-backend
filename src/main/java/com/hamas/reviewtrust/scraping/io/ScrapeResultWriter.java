package com.hamas.reviewtrust.scraping.io;

import app.scraper.amazon.ReviewHistogramParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hamas.reviewtrust.common.hash.TextHash;
import com.hamas.reviewtrust.scraping.AmazonReviewScraper;
import com.hamas.reviewtrust.scraping.SakuraScorer;
import com.hamas.reviewtrust.scraping.ScrapingProps;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ScrapeResultWriter {

    private static final Pattern ASIN_PATTERN = Pattern.compile("/(?:dp|gp/product)/([A-Z0-9]{10})(?:/|\\?|$)");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final ThreadLocal<BufferedWriter> activeWriter = new ThreadLocal<>();

    private final ScrapingProps props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScrapeResultWriter(ScrapingProps props) {
        this.props = props;
    }

    public Path write(String dataset,
                      String url,
                      List<AmazonReviewScraper.ReviewDetail> reviews,
                      ReviewHistogramParser.Result histogram,
                      Instant capturedAt,
                      String productName) throws IOException {
        List<AmazonReviewScraper.ReviewDetail> safeReviews =
                reviews == null ? List.of() : List.copyOf(reviews);

        Instant effectiveCapturedAt = capturedAt != null ? capturedAt : Instant.now();
        String ds = slugOr(dataset, "adhoc");
        String asin = extractAsin(url).orElse("unknown");
        String timestamp = TS.format(effectiveCapturedAt);

        Path base = Paths.get(props.getOutDir()).toAbsolutePath().normalize();
        Path dir = props.isOutPerProduct()
                ? base.resolve(ds).resolve(asin)
                : base.resolve(ds);

        Files.createDirectories(dir);

        String ext = switch (safe(props.getOutFormat()).toLowerCase(Locale.ROOT)) {
            case "csv" -> "csv";
            case "json" -> "json";
            default -> "ndjson";
        };

        Path file = props.isOutPerProduct()
                ? dir.resolve(asin + "__" + timestamp + "." + ext)
                : dir.resolve(ds + "." + ext);

        return switch (ext) {
            case "csv" -> writeCsv(file, url, asin, ds, safeReviews);
            case "json" -> writeJson(file, url, asin, ds, safeReviews);
            default -> writeNdjson(file, url, asin, ds, safeReviews, histogram, effectiveCapturedAt, productName);
        };
    }

    public Path writeHistogramMeta(String dataset,
                                   String url,
                                   ReviewHistogramParser.Result histogram,
                                   Instant capturedAt) throws IOException {
        return write(dataset, url, List.of(), histogram, capturedAt, null);
    }

    public Path writeHistogramMeta(String dataset,
                                   String url,
                                   ReviewHistogramParser.Result histogram,
                                   Instant capturedAt,
                                   String productName) throws IOException {
        return write(dataset, url, List.of(), histogram, capturedAt, productName);
    }

    public Path write(String dataset,
                      String url,
                      List<AmazonReviewScraper.ReviewDetail> reviews) throws IOException {
        return write(dataset, url, reviews, null, Instant.now());
    }

    public Path write(String dataset,
                      String url,
                      List<AmazonReviewScraper.ReviewDetail> reviews,
                      ReviewHistogramParser.Result histogram,
                      Instant capturedAt) throws IOException {
        return write(dataset, url, reviews, histogram, capturedAt, null);
    }

    private void writeHistogramMetaLine(String dataset,
                                        String asin,
                                        ReviewHistogramParser.Result histogram,
                                        Instant capturedAt,
                                        String productName) throws IOException {
        BufferedWriter writer = activeWriter.get();
        if (writer == null) {
            throw new IllegalStateException("writeHistogramMeta called outside active writer scope");
        }

        ObjectNode meta = mapper.createObjectNode();
        meta.put("type", "histogram");
        if (dataset != null) {
            meta.put("dataset", dataset);
        } else {
            meta.putNull("dataset");
        }
        if (asin != null) {
            meta.put("asin", asin);
        } else {
            meta.putNull("asin");
        }
        if (capturedAt != null) {
            meta.put("captured_at", capturedAt.toString());
        } else {
            meta.putNull("captured_at");
        }
        if (productName != null) {
            meta.put("productName", productName);
        } else {
            meta.putNull("productName");
        }

        ReviewHistogramParser.Result safeHistogram = histogram;
        double average = 0.0;
        int reviewCount = 0;
        if (safeHistogram != null) {
            if (safeHistogram.averageRating != null) {
                average = safeHistogram.averageRating.doubleValue();
            }
            reviewCount = safeHistogram.reviewCount;
        }
        meta.put("rating_average", average);
        meta.put("rating_count", reviewCount);

        ObjectNode histogramNode = mapper.createObjectNode();
        for (int star = 5; star >= 1; star--) {
            int value = 0;
            if (safeHistogram != null) {
                Integer extracted = safeHistogram.percentageByStar.get(star);
                if (extracted != null) {
                    value = Math.max(0, Math.min(100, extracted));
                }
            }
            histogramNode.put(String.valueOf(star), value);
        }
        meta.set("ratings_histogram", histogramNode);

        writer.write(mapper.writeValueAsString(meta));
        writer.write('\n');
        writer.flush();
    }

    private Path writeNdjson(Path file,
                             String url,
                             String asin,
                             String dataset,
                             List<AmazonReviewScraper.ReviewDetail> reviews,
                             ReviewHistogramParser.Result histogram,
                             Instant capturedAt,
                             String productName) throws IOException {
        boolean append = Files.exists(file);
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            activeWriter.set(writer);
            if (!append) {
                writeHistogramMetaLine(dataset, asin, histogram, capturedAt, productName);
            }
            ObjectWriter objectWriter = mapper.writer();
            List<Map<String, Object>> rows = buildRows(url, asin, dataset, reviews);
            for (Map<String, Object> row : rows) {
                writer.write(objectWriter.writeValueAsString(row));
                writer.write('\n');
            }
        } finally {
            activeWriter.remove();
        }
        return file;
    }

    private Path writeJson(Path file,
                           String url,
                           String asin,
                           String dataset,
                           List<AmazonReviewScraper.ReviewDetail> reviews) throws IOException {
        List<Map<String, Object>> rows = buildRows(url, asin, dataset, reviews);
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), rows);
        return file;
    }

    private Path writeCsv(Path file,
                          String url,
                          String asin,
                          String dataset,
                          List<AmazonReviewScraper.ReviewDetail> reviews) throws IOException {
        boolean exists = Files.exists(file);
        Files.createDirectories(file.getParent());

        try (OutputStream os = Files.newOutputStream(
                file,
                exists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             OutputStreamWriter osWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(
                     osWriter,
                     exists
                             ? CSVFormat.DEFAULT
                             : CSVFormat.DEFAULT.builder()
                                     .setHeader("dataset", "asin", "url", "reviewId", "rating", "dateText",
                                             "title", "author", "body", "bodyLength", "ratingText",
                                             "heuristic_sakura_score", "heuristic_reasons", "sakura_flag",
                                             "collectedAt")
                                     .build())) {
            if (!exists && props.isCsvWithBom()) {
                os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            }

            List<Map<String, Object>> rows = buildRows(url, asin, dataset, reviews);
            for (Map<String, Object> row : rows) {
                printer.printRecord(
                        row.get("dataset"),
                        row.get("asin"),
                        row.get("url"),
                        row.get("reviewId"),
                        row.get("rating"),
                        row.get("dateText"),
                        row.get("title"),
                        row.get("author"),
                        row.get("body"),
                        row.get("bodyLength"),
                        row.get("ratingText"),
                        row.get("heuristic_sakura_score"),
                        row.get("heuristic_reasons"),
                        row.get("sakura_flag"),
                        row.get("collectedAt")
                );
            }
        }
        return file;
    }

    private List<Map<String, Object>> buildRows(String url,
                                                String asin,
                                                String dataset,
                                                List<AmazonReviewScraper.ReviewDetail> reviews) {
        List<Map<String, Object>> rows = new ArrayList<>(reviews.size());
        if (reviews.isEmpty()) {
            return rows;
        }
        StatsBundle bundle = buildStats(reviews);
        SakuraScorer.ProductStats stats = bundle.stats();
        List<String> bodyHashes = bundle.bodyHashes();
        List<String> authorKeys = bundle.authors();
        for (int i = 0; i < reviews.size(); i++) {
            AmazonReviewScraper.ReviewDetail detail = reviews.get(i);
            Map<String, Object> row = toRow(url, asin, dataset, detail);
            SakuraScorer.ScoreResult score = SakuraScorer.score(
                    bodyHashes.get(i),
                    authorKeys.get(i),
                    detail.body(),
                    detail.bodyLength(),
                    ratingToInt(detail.rating()),
                    stats
            );
            row.put("heuristic_sakura_score", score.score);
            row.put("heuristic_reasons", score.reasons);
            row.put("sakura_flag", score.flag);
            rows.add(row);
        }
        return rows;
    }

    private StatsBundle buildStats(List<AmazonReviewScraper.ReviewDetail> reviews) {
        Map<String, Integer> bodyHashCounts = new HashMap<>();
        Map<String, Integer> authorCounts = new HashMap<>();
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            histogram.put(star, 0);
        }
        List<String> bodyHashes = new ArrayList<>(reviews.size());
        List<String> authors = new ArrayList<>(reviews.size());
        for (AmazonReviewScraper.ReviewDetail review : reviews) {
            String normalizedBody = normalizeBody(review.body());
            String bodyHash = TextHash.sha256Hex(normalizedBody);
            bodyHashes.add(bodyHash);
            if (!bodyHash.isEmpty()) {
                bodyHashCounts.merge(bodyHash, 1, Integer::sum);
            }

            String authorKey = normalizeAuthor(review.author());
            authors.add(authorKey);
            if (!authorKey.isEmpty()) {
                authorCounts.merge(authorKey, 1, Integer::sum);
            }

            int star = ratingToInt(review.rating());
            histogram.merge(star, 1, Integer::sum);
        }
        SakuraScorer.ProductStats stats =
                new SakuraScorer.ProductStats(bodyHashCounts, authorCounts, histogram, reviews.size());
        return new StatsBundle(stats, bodyHashes, authors);
    }

    private static String normalizeBody(String body) {
        if (body == null) {
            return "";
        }
        return body.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizeAuthor(String author) {
        if (author == null) {
            return "";
        }
        String trimmed = author.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static int ratingToInt(double rating) {
        if (Double.isNaN(rating)) {
            return 0;
        }
        int rounded = (int) Math.round(rating);
        if (rounded < 1) {
            return 1;
        }
        if (rounded > 5) {
            return 5;
        }
        return rounded;
    }

    private record StatsBundle(SakuraScorer.ProductStats stats,
                               List<String> bodyHashes,
                               List<String> authors) {
    }

    private Map<String, Object> toRow(String url,
                                      String asin,
                                      String dataset,
                                      AmazonReviewScraper.ReviewDetail detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dataset", dataset);
        row.put("asin", asin);
        row.put("url", url);
        row.put("reviewId", nvl(detail.reviewId()));
        row.put("rating", detail.rating());
        row.put("dateText", nvl(detail.dateText()));
        row.put("title", nvl(detail.title()));
        row.put("author", nvl(detail.author()));
        row.put("body", nvl(detail.body()));
        row.put("bodyLength", detail.bodyLength());
        row.put("ratingText", nvl(detail.ratingText()));
        row.put("collectedAt", Instant.now().toString());
        return row;
    }

    private Optional<String> extractAsin(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher matcher = ASIN_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String slugOr(String value, String fallback) {
        String trimmed = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        trimmed = trimmed.replaceAll("^_+|_+$", "");
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
