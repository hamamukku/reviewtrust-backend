package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.config.IntakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("dev")
public class ReviewSnapshotScanner {

    private static final Logger log = LoggerFactory.getLogger(ReviewSnapshotScanner.class);

    private final ReviewIntakeService reviewIntakeService;
    private final IntakeProperties intakeProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, AsinBucket> buckets = new LinkedHashMap<>();

    public ReviewSnapshotScanner(ReviewIntakeService reviewIntakeService,
                                 IntakeProperties intakeProperties,
                                 ObjectMapper objectMapper) {
        this.reviewIntakeService = reviewIntakeService;
        this.intakeProperties = intakeProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        buckets.clear();
        List<Path> files = reviewIntakeService.getCandidateFiles(null);
        log.info("ReviewSnapshotScanner - scanning directories={} candidateFiles={}",
                intakeProperties.dirList(), files.size());

        int histogramCount = 0;
        int reviewCount = 0;
        int skipped = 0;

        for (Path file : files) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    JsonNode node;
                    try {
                        node = objectMapper.readTree(trimmed);
                    } catch (Exception parseEx) {
                        log.warn("[snapshot] skip parse failure file={} line={} cause={}",
                                file.getFileName(), lineNo, parseEx.getMessage());
                        skipped++;
                        continue;
                    }

                    String dataset = textOrNull(node, "dataset");
                    if (!isAdhocDataset(dataset)) {
                        skipped++;
                        continue;
                    }

                    String asin = textOrNull(node, "asin");
                    if (!StringUtils.hasText(asin)) {
                        skipped++;
                        continue;
                    }
                    String asinKey = asin.trim().toUpperCase(Locale.ROOT);
                    AsinBucket bucket = buckets.computeIfAbsent(asinKey, key -> new AsinBucket());
                    bucket.addSource(file.toString());
                    bucket.ensureDataset(dataset);

                    if (isHistogram(node)) {
                        bucket.histogram = toHistogram(node, file.toString(), dataset);
                        histogramCount++;
                        continue;
                    }

                    ReviewRecord record = toReviewRecord(node, file.toString());
                    if (record == null) {
                        skipped++;
                        continue;
                    }
                    bucket.reviews.add(record);
                    reviewCount++;
                }
            } catch (Exception e) {
                log.warn("[snapshot] failed to scan file={} cause={}", file, e.toString());
            }
        }

        log.info("ReviewSnapshotScanner - scannedFiles={} histograms={} loadedEntries={} skippedEntries={} dirs={}",
                files.size(), histogramCount, reviewCount, skipped, intakeProperties.dirList());
    }

    public List<SnapshotSummary> listSummaries(boolean includeUploaded) {
        List<SnapshotSummary> summaries = new ArrayList<>(buckets.size());
        for (Map.Entry<String, AsinBucket> entry : buckets.entrySet()) {
            String asin = entry.getKey();
            AsinBucket bucket = entry.getValue();
            HistogramEntry histogram = bucket.histogram;
            Double ratingAverage = histogram != null && histogram.ratingAverage() != null
                    ? histogram.ratingAverage()
                    : averageRating(bucket.reviews);
            Integer ratingCount = histogram != null && histogram.ratingCount() != null
                    ? histogram.ratingCount()
                    : bucket.reviews.size();
            int reviewCount = bucket.reviews.size();
            String capturedAt = resolveCapturedAt(bucket);
            List<String> sourcePaths = List.copyOf(bucket.sources);
            String productName = histogram != null ? histogram.productName() : null;
            Double score = toScore(ratingAverage);

            summaries.add(new SnapshotSummary(
                    asin,
                    bucket.dataset,
                    null,
                    null,
                    ratingAverage,
                    ratingCount,
                    reviewCount,
                    null,
                    capturedAt,
                    sourcePaths,
                    productName,
                    score
            ));
        }
        summaries.sort(Comparator.comparing(SnapshotSummary::asin));
        return summaries;
    }

    public AsinReviews getReviewsOf(String asin) {
        if (!StringUtils.hasText(asin)) {
            return new AsinReviews(null, null, null, List.of(), List.of());
        }
        String asinKey = asin.trim().toUpperCase(Locale.ROOT);
        AsinBucket bucket = buckets.get(asinKey);
        if (bucket == null) {
            return new AsinReviews(asinKey, null, null, List.of(), List.of());
        }
        List<ReviewRecord> reviewRecords = List.copyOf(bucket.reviews);
        return new AsinReviews(
                asinKey,
                bucket.dataset,
                resolveCapturedAt(bucket),
                reviewRecords,
                List.copyOf(bucket.sources)
        );
    }

    public Optional<Snapshot> getSnapshot(String asin, String dataset) {
        if (!StringUtils.hasText(asin)) {
            return Optional.empty();
        }
        String asinKey = asin.trim().toUpperCase(Locale.ROOT);
        AsinBucket bucket = buckets.get(asinKey);
        if (bucket == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(dataset)
                && StringUtils.hasText(bucket.dataset)
                && !dataset.trim().equalsIgnoreCase(bucket.dataset)) {
            return Optional.empty();
        }
        Double score = bucket.histogram != null ? toScore(bucket.histogram.ratingAverage()) : null;
        String productName = bucket.histogram != null ? bucket.histogram.productName() : null;
        return Optional.of(new Snapshot(
                asinKey,
                bucket.dataset,
                List.copyOf(bucket.reviews),
                bucket.histogram,
                List.copyOf(bucket.sources),
                productName,
                score
        ));
    }

    private HistogramEntry toHistogram(JsonNode node, String sourcePath, String dataset) {
        Double ratingAverage = numberOrNull(node, "rating_average");
        Integer ratingCount = integerOrNull(node, "rating_count");
        String capturedAt = textOrNull(node, "captured_at");
        Map<Integer, Integer> ratings = new LinkedHashMap<>();
        JsonNode histogramNode = node.path("ratings_histogram");
        if (histogramNode != null && histogramNode.isObject()) {
            histogramNode.fields().forEachRemaining(entry -> {
                try {
                    int star = Integer.parseInt(entry.getKey());
                    int value = entry.getValue().isNumber()
                            ? entry.getValue().intValue()
                            : Integer.parseInt(entry.getValue().asText());
                    ratings.put(star, value);
                } catch (Exception ignore) {
                    // ignore malformed star rows
                }
            });
        }
        String productName = textOrNull(node, "productName");
        return new HistogramEntry(dataset, ratingAverage, ratingCount, capturedAt, ratings, sourcePath, productName);
    }

    private ReviewRecord toReviewRecord(JsonNode node, String sourcePath) {
        String reviewId = textOrNull(node, "reviewId");
        if (!StringUtils.hasText(reviewId)) {
            return null;
        }
        Double rating = numberOrNull(node, "rating");
        String title = textOrNull(node, "title");
        String author = textOrNull(node, "author");
        String body = textOrNull(node, "body");
        Integer bodyLength = integerOrNull(node, "bodyLength");
        String ratingText = textOrNull(node, "ratingText");
        String dateText = textOrNull(node, "dateText");
        String url = textOrNull(node, "url");
        String collectedAt = textOrNull(node, "collectedAt");
        Double heuristicScore = numberOrNull(node, "heuristic_sakura_score");
        String heuristicReasons = textOrNull(node, "heuristic_reasons");
        Boolean sakuraFlag = booleanOrNull(node, "sakura_flag");
        return new ReviewRecord(
                reviewId,
                rating,
                title,
                author,
                body,
                bodyLength,
                ratingText,
                dateText,
                url,
                collectedAt,
                heuristicScore,
                heuristicReasons,
                sakuraFlag,
                sourcePath
        );
    }

    private static Double averageRating(List<ReviewRecord> records) {
        DoubleSummaryStatistics stats = records.stream()
                .map(ReviewRecord::rating)
                .filter(r -> r != null && !r.isNaN())
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        if (stats.getCount() == 0) {
            return null;
        }
        return stats.getAverage();
    }

    private static String resolveCapturedAt(AsinBucket bucket) {
        if (bucket.histogram != null && StringUtils.hasText(bucket.histogram.capturedAt())) {
            return bucket.histogram.capturedAt();
        }
        Optional<String> latest = bucket.reviews.stream()
                .map(ReviewRecord::collectedAt)
                .filter(StringUtils::hasText)
                .max(ReviewSnapshotScanner::compareInstantStrings);
        return latest.orElse(null);
    }

    private static int compareInstantStrings(String left, String right) {
        try {
            Instant l = Instant.parse(left);
            Instant r = Instant.parse(right);
            return l.compareTo(r);
        } catch (DateTimeParseException ex) {
            return left.compareTo(right);
        }
    }

    private static boolean isHistogram(JsonNode node) {
        if (node == null || !node.has("type")) {
            return false;
        }
        String type = node.path("type").asText();
        return StringUtils.hasText(type) && "histogram".equalsIgnoreCase(type.trim());
    }

    private static boolean isAdhocDataset(String dataset) {
        return StringUtils.hasText(dataset) && "adhoc".equalsIgnoreCase(dataset.trim());
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Double numberOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer integerOrNull(JsonNode node, String field) {
        Double number = numberOrNull(node, field);
        if (number == null) {
            return null;
        }
        return (int) Math.round(number);
    }

    private static Boolean booleanOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.intValue() != 0;
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return "1".equals(text.trim()) || Boolean.parseBoolean(text.trim());
    }

    private static Double toScore(Double ratingAverage) {
        if (ratingAverage == null || ratingAverage.isNaN()) {
            return null;
        }
        double clamped = Math.max(0.0d, Math.min(5.0d, ratingAverage));
        double scaled = (clamped / 5.0d) * 100.0d;
        return Math.round(scaled * 10.0d) / 10.0d;
    }

    private static final class AsinBucket {
        private HistogramEntry histogram;
        private final List<ReviewRecord> reviews = new ArrayList<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private String dataset;

        void addSource(String path) {
            if (StringUtils.hasText(path)) {
                sources.add(path);
            }
        }

        void ensureDataset(String value) {
            if (!StringUtils.hasText(dataset) && StringUtils.hasText(value)) {
                dataset = value;
            }
        }
    }

    public record HistogramEntry(String dataset,
                                 Double ratingAverage,
                                 Integer ratingCount,
                                 String capturedAt,
                                 Map<Integer, Integer> ratingsHistogram,
                                 String sourcePath,
                                 String productName) {
    }

    public record SnapshotSummary(String asin,
                                  String dataset,
                                  @Nullable String title,
                                  @Nullable Integer priceYen,
                                  @Nullable Double ratingAverage,
                                  @Nullable Integer ratingCount,
                                  Integer reviewCount,
                                  @Nullable String summary,
                                  @Nullable String capturedAt,
                                  List<String> sourcePaths,
                                  @Nullable String productName,
                                  @Nullable Double score) {
    }

    public record AsinReviews(String asin,
                              String dataset,
                              String capturedAt,
                              List<ReviewRecord> reviews,
                              List<String> sourcePaths) {
    }

    public record Snapshot(String asin,
                           @Nullable String dataset,
                           List<ReviewRecord> reviews,
                           @Nullable HistogramEntry histogram,
                           List<String> sourcePaths,
                           @Nullable String productName,
                           @Nullable Double score) {
        public Optional<HistogramEntry> histogramOptional() {
            return Optional.ofNullable(histogram);
        }
    }

    public record ReviewRecord(String reviewId,
                               Double rating,
                               String title,
                               String author,
                               String body,
                               Integer bodyLength,
                               String ratingText,
                               String dateText,
                               String url,
                               String collectedAt,
                               Double heuristicSakuraScore,
                               String heuristicReasons,
                               Boolean sakuraFlag,
                               String sourcePath) {
    }
}
