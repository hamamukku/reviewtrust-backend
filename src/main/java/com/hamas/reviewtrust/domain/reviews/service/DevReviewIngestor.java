package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.service.ReviewSnapshotScanner.HistogramEntry;
import com.hamas.reviewtrust.domain.reviews.service.ReviewSnapshotScanner.ReviewRecord;
import com.hamas.reviewtrust.domain.reviews.service.ReviewSnapshotScanner.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Profile("dev")
@Service
public class DevReviewIngestor {

    private static final Logger log = LoggerFactory.getLogger(DevReviewIngestor.class);
    private static final String SOURCE_AMAZON = "AMAZON";
    private static final Pattern JP_DATE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");

    private static final String UPSERT_SQL = """
            INSERT INTO reviews(
                product_id, source, source_review_id, stars, rating, title, author,
                body, body_length, rating_text, posted_at, collected_at, heuristics,
                created_at, updated_at
            )
            VALUES (
                :productId,
                :source,
                :sourceReviewId,
                :stars,
                :rating,
                :title,
                :author,
                :body,
                :bodyLength,
                :ratingText,
                :postedAt,
                :collectedAt,
                :heuristics::jsonb,
                now(),
                now()
            )
            ON CONFLICT (source, source_review_id)
            DO UPDATE SET
                product_id   = EXCLUDED.product_id,
                stars        = EXCLUDED.stars,
                rating       = EXCLUDED.rating,
                title        = EXCLUDED.title,
                author       = EXCLUDED.author,
                body         = EXCLUDED.body,
                body_length  = EXCLUDED.body_length,
                rating_text  = EXCLUDED.rating_text,
                posted_at    = COALESCE(EXCLUDED.posted_at, reviews.posted_at),
                collected_at = COALESCE(EXCLUDED.collected_at, reviews.collected_at),
                heuristics   = EXCLUDED.heuristics,
                updated_at   = now()
            """;

    private static final String UPSERT_PRODUCT_STATS_SQL = """
            INSERT INTO product_stats(
                product_id, rating_average, rating_count, ratings_histogram, captured_at, updated_at
            )
            VALUES (
                :productId,
                :ratingAverage,
                :ratingCount,
                CAST(:ratingsHistogram AS jsonb),
                :capturedAt,
                now()
            )
            ON CONFLICT (product_id)
            DO UPDATE SET
                rating_average = EXCLUDED.rating_average,
                rating_count = EXCLUDED.rating_count,
                ratings_histogram = EXCLUDED.ratings_histogram,
                captured_at = EXCLUDED.captured_at,
                updated_at = now()
            """;

    private static final String REFRESH_HISTOGRAM_SQL = """
            INSERT INTO product_stats (product_id, ratings_histogram, updated_at)
            VALUES (
                :productId,
                COALESCE((
                    SELECT jsonb_object_agg(rating::text, cnt)
                    FROM (
                        SELECT rating, COUNT(*) AS cnt
                        FROM reviews
                        WHERE product_id = :productId
                          AND rating IS NOT NULL
                        GROUP BY rating
                    ) sub
                ), '{}'::jsonb),
                now()
            )
            ON CONFLICT (product_id)
            DO UPDATE SET
                ratings_histogram = EXCLUDED.ratings_histogram,
                updated_at = now()
            """;

    private final ReviewSnapshotScanner snapshotScanner;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    public DevReviewIngestor(ReviewSnapshotScanner snapshotScanner,
                             NamedParameterJdbcTemplate namedJdbcTemplate,
                             ProductRepository productRepository,
                             ObjectMapper objectMapper,
                             ApplicationContext context) {
        this.snapshotScanner = snapshotScanner;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.context = context;
    }

    // 非Tx。DB書込みは ingestInternal() に集約。再計算はコミット後に実行。

    public IngestResult ingest(String asin, String dataset) {
        IngestExecution exec;
        try {
            exec = ingestInternal(asin, dataset);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("INGEST_FAIL asin={} reason={}", asin, ex.getReason());
            }
            throw ex;
        } catch (DataAccessException ex) {
            log.error("INGEST_FAIL asin={} cause={}", asin, ex.getMessage(), ex);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "failed to ingest reviews");
        }

        IngestResult result = exec.result();
        try {
            // REQUIRES_NEW を正しく適用するため AOP プロキシ経由で呼び出す
            ScoreService proxy = context.getBean(ScoreService.class);
            var recomputeResult = proxy.recomputeForProduct(exec.productId());
            log.info("INGEST_OK asin={} productId={} inserted={} updated={} pos={} neg={} capturedAt={} recomputedScore={}",
                    result.asin(),
                    exec.productId(),
                    result.inserted(),
                    result.updated(),
                    result.positives(),
                    result.negatives(),
                    result.capturedAt(),
                    recomputeResult != null ? recomputeResult.score() : null);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("RECOMPUTE_FAIL asin={} productId={} reason={}", asin, exec.productId(), ex.getReason());
            }
            throw ex;
        } catch (DataAccessException ex) {
            log.error("RECOMPUTE_FAIL asin={} productId={} cause={}", asin, exec.productId(), ex.getMessage(), ex);
        }
        return result;
    }

    // DB書込みはこのTxで完了させる。再計算は呼び出し元で実行。
    @Transactional(propagation = Propagation.REQUIRED)
    private IngestExecution ingestInternal(String asin, String dataset) {
        String asinKey = normalizeAsin(asin);
        if (!StringUtils.hasText(asinKey)) throw new ResponseStatusException(BAD_REQUEST, "asin is required");

        String datasetKey = normalizeDataset(dataset);
        if (!StringUtils.hasText(datasetKey)) throw new ResponseStatusException(BAD_REQUEST, "dataset is required");

        Snapshot snapshot = snapshotScanner.getSnapshot(asinKey, datasetKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "snapshot not found for asin/dataset"));

        Product product = productRepository.findByAsin(asinKey)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "product not registered for asin"));

        List<ReviewRecord> raw = snapshot.reviews() != null ? snapshot.reviews() : List.of();
        List<ReviewRecord> ingestable = new ArrayList<>(raw.size());
        for (ReviewRecord r : raw) {
            if (r != null && StringUtils.hasText(r.reviewId())) ingestable.add(r);
        }

        // 既存IDの抽出（更新件数見積もり用）
        Set<String> existingIds = findExistingReviewIds(ingestable);

        AtomicInteger positives = new AtomicInteger();
        AtomicInteger negatives = new AtomicInteger();

        // バッチUPSERT
        List<SqlParameterSource> batchParams = new ArrayList<>(ingestable.size());
        for (ReviewRecord record : ingestable) {
            batchParams.add(buildReviewParams(product.getId(), record));
            updateSentimentCounters(record, positives, negatives);
        }
        if (!batchParams.isEmpty()) {
            namedJdbcTemplate.batchUpdate(UPSERT_SQL, batchParams.toArray(SqlParameterSource[]::new));
        }

        // スナップショット由来のヒストグラムを有効な場合のみ反映
        boolean snapSaved = snapshot.histogramOptional()
                .filter(this::hasMeaningfulHistogram)
                .map(h -> upsertProductStats(product.getId(), h))
                .orElse(false);

        // 最終確定は必ずDB再集計の結果で上書き（0上書きの再発防止）
        int rows = refreshRatingsHistogram(product.getId());
        boolean histogramSaved = snapSaved || rows > 0;

        Instant capturedAt = resolveCapturedAt(snapshot);
        IngestResult result = new IngestResult(
                asinKey,
                Math.max(0, batchParams.size() - existingIds.size()),
                existingIds.size(),
                positives.get(),
                negatives.get(),
                histogramSaved,
                capturedAt
        );
        return new IngestExecution(product.getId(), result);
    }

    private MapSqlParameterSource buildReviewParams(UUID productId, ReviewRecord record) {
        Double rating = record.rating();
        Integer stars = null;
        BigDecimal ratingValue = null;
        if (rating != null && !rating.isNaN()) {
            stars = (int) Math.round(Math.max(0, Math.min(5, rating)));
            ratingValue = BigDecimal.valueOf(rating);
        }

        OffsetDateTime postedAt = parsePostedAt(record.dateText());
        OffsetDateTime collectedAt = parseCollectedAt(record.collectedAt());

        String body = record.body();
        if (!StringUtils.hasText(body)) body = null;

        Integer bodyLength = record.bodyLength();
        if (bodyLength == null && body != null) bodyLength = body.length();

        return new MapSqlParameterSource()
                .addValue("productId", productId, Types.OTHER)
                .addValue("source", SOURCE_AMAZON, Types.VARCHAR)
                .addValue("sourceReviewId", record.reviewId().trim(), Types.VARCHAR)
                .addValue("stars", stars, Types.SMALLINT)            // ← 列型に合わせる
                .addValue("rating", ratingValue, Types.NUMERIC)
                .addValue("title", nullIfBlank(record.title()), Types.VARCHAR)
                .addValue("author", nullIfBlank(record.author()), Types.VARCHAR)
                .addValue("body", body, Types.VARCHAR)
                .addValue("bodyLength", bodyLength, Types.INTEGER)
                .addValue("ratingText", nullIfBlank(record.ratingText()), Types.VARCHAR)
                .addValue("postedAt", postedAt, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("collectedAt", collectedAt, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("heuristics", buildHeuristicsJson(record), Types.VARCHAR);
    }

    private void updateSentimentCounters(ReviewRecord record,
                                         AtomicInteger positives,
                                         AtomicInteger negatives) {
        if (record == null || record.rating() == null || record.rating().isNaN()) return;
        double r = record.rating();
        if (r >= 4.0d) positives.incrementAndGet();
        else if (r <= 2.0d) negatives.incrementAndGet();
    }

    private boolean hasMeaningfulHistogram(HistogramEntry histogram) {
        if (histogram == null || histogram.ratingsHistogram() == null) return false;
        return histogram.ratingsHistogram().values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum() > 0;
    }

    private Instant resolveCapturedAt(Snapshot snapshot) {
        if (snapshot == null) return null;
        Instant fromHeader = snapshot.histogramOptional()
                .map(HistogramEntry::capturedAt)
                .map(DevReviewIngestor::parseInstantQuietly)
                .orElse(null);
        if (fromHeader != null) return fromHeader;

        List<ReviewRecord> reviews = snapshot.reviews();
        if (reviews == null || reviews.isEmpty()) return null;

        return reviews.stream()
                .map(ReviewRecord::collectedAt)
                .map(DevReviewIngestor::parseInstantQuietly)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean upsertProductStats(UUID productId, HistogramEntry histogram) {
        BigDecimal average = histogram.ratingAverage() != null
                ? BigDecimal.valueOf(histogram.ratingAverage())
                : null;
        Integer ratingCount = histogram.ratingCount();
        String histogramJson = toJson(histogram.ratingsHistogram());
        OffsetDateTime capturedAt = parseCollectedAt(histogram.capturedAt());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("ratingAverage", average, Types.NUMERIC)
                .addValue("ratingCount", ratingCount, Types.INTEGER)
                .addValue("ratingsHistogram", histogramJson, Types.VARCHAR)
                .addValue("capturedAt", capturedAt, Types.TIMESTAMP_WITH_TIMEZONE);

        return namedJdbcTemplate.update(UPSERT_PRODUCT_STATS_SQL, params) > 0;
    }

    // 更新行数を返すようにし、呼び出し側で保存フラグに反映
    private int refreshRatingsHistogram(UUID productId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productId", productId, Types.OTHER);
        int updated = namedJdbcTemplate.update(REFRESH_HISTOGRAM_SQL, params);
        log.info("[DevReviewIngestor] product_stats histogram refreshed productId={} rows={}", productId, updated);
        return updated;
    }

    private Set<String> findExistingReviewIds(List<ReviewRecord> records) {
        List<String> ids = records.stream()
                .map(ReviewRecord::reviewId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty()) return new HashSet<>();
        var params = new MapSqlParameterSource()
                .addValue("source", SOURCE_AMAZON)
                .addValue("ids", ids);
        String sql = """
                SELECT source_review_id
                  FROM reviews
                 WHERE source = :source
                   AND source_review_id IN (:ids)
                """;
        return new HashSet<>(namedJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("source_review_id")));
    }

    private static String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OffsetDateTime parseCollectedAt(String collectedAt) {
        if (!StringUtils.hasText(collectedAt)) return null;
        try {
            return OffsetDateTime.parse(collectedAt.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Instant parseInstantQuietly(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private OffsetDateTime parsePostedAt(String dateText) {
        if (!StringUtils.hasText(dateText)) return null;
        Matcher m = JP_DATE_PATTERN.matcher(dateText);
        if (!m.find()) return null;
        try {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));
            LocalDate date = LocalDate.of(y, mo, d);
            return date.atStartOfDay(JAPAN_ZONE).toOffsetDateTime();
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildHeuristicsJson(ReviewRecord record) {
        ObjectNode node = objectMapper.createObjectNode();
        if (record.heuristicSakuraScore() != null) node.put("sakura_score", record.heuristicSakuraScore());
        if (record.sakuraFlag() != null) node.put("sakura_flag", record.sakuraFlag());
        if (StringUtils.hasText(record.heuristicReasons())) node.put("reasons", record.heuristicReasons());
        return node.isEmpty() ? "{}" : node.toString();
    }

    private String toJson(Map<Integer, Integer> histogram) {
        if (histogram == null || histogram.isEmpty()) return "{}";
        Map<String, Integer> converted = new LinkedHashMap<>(histogram.size());
        histogram.forEach((k, v) -> { if (k != null) converted.put(String.valueOf(k), v); });
        if (converted.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(converted);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize histogram", e);
        }
    }

    private String normalizeAsin(String asin) {
        if (!StringUtils.hasText(asin)) return null;
        return asin.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDataset(String dataset) {
        if (!StringUtils.hasText(dataset)) return null;
        return dataset.trim().toLowerCase(Locale.ROOT);
    }

    private record IngestExecution(UUID productId, IngestResult result) { }

    public record IngestResult(String asin,
                               int inserted,
                               int updated,
                               int positives,
                               int negatives,
                               boolean histogramSaved,
                               Instant capturedAt) {}
}
