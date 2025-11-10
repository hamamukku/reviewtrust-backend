package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.common.text.TextNormalizer;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewScoreRepository;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;

/**
 * Lightweight sakura scoring service used by the public Scores API.
 *
 * The service extracts heuristic features from the reviews table, evaluates them
 * against scoring/thresholds.yml, and persists the outcome to review_scores (source="SITE").
 */
@Service("productScoreService")
public class ScoreService {

    private static final Logger log = LoggerFactory.getLogger(ScoreService.class);

    private static final String SCORE_SOURCE = "SITE";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final int SHORT_REVIEW_THRESHOLD = 60;

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://|www\\.|\\.co(m|\\.jp)|\\.jp");
    private static final Pattern SYMBOL_RUN_PATTERN = Pattern.compile("([!！?？.,。、〜～ー\\-])\\1{2,}");

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ThresholdProvider thresholdProvider;
    private final ReviewScoreRepository reviewScoreRepository;
    private final ConcurrentMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public ScoreService(NamedParameterJdbcTemplate namedJdbcTemplate,
                        ObjectMapper objectMapper,
                        ThresholdProvider thresholdProvider,
                        ReviewScoreRepository reviewScoreRepository) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
        this.thresholdProvider = thresholdProvider;
        this.reviewScoreRepository = reviewScoreRepository;
    }

    /** Compute without persisting. */
    public ProductScore calculateScore(UUID productId) {
        if (productId == null) return defaultScore(null);
        try {
            return calculateSakuraScore(productId);
        } catch (Exception e) {
            log.warn("[ScoreService] Failed to compute sakura score for {}", productId, e);
            return defaultScore(productId);
        }
    }

    /** Cached compute (no persistence). */
    public ProductScore getCachedScore(UUID productId) {
        if (productId == null) return defaultScore(null);
        Instant now = Instant.now();
        CacheEntry cached = cache.get(productId);
        if (cached != null && cached.expiresAt().isAfter(now)) return cached.score();
        ProductScore fresh = calculateScore(productId);
        cache.put(productId, new CacheEntry(fresh, now.plus(CACHE_TTL)));
        return fresh;
    }

    /** Recompute and persist in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductScore recomputeForProduct(UUID productId) {
        if (productId == null) return defaultScore(null);
        log.info("[ScoreService] RECOMPUTE start productId={}", productId);

        ProductScore updated = calculateScore(productId);

        // Persist computed score (REQUIRES_NEW applied at method level)
        persistScore(productId,
                updated.score(),
                updated.rank(),
                updated.sakuraJudge(),
                updated.flags(),
                updated.rules(),
                updated.metrics());

        cache.put(productId, new CacheEntry(updated, Instant.now().plus(CACHE_TTL)));
        log.info("[ScoreService] RECOMPUTE result productId={} score={} rank={} judge={}",
                productId, updated.score(), updated.rank(), updated.sakuraJudge());
        return updated;
    }

    /* --------------------------------------------------------------------- */
    /* Core pipeline                                                         */
    /* --------------------------------------------------------------------- */

    /** Pure compute. Do NOT persist here. */
    private ProductScore calculateSakuraScore(UUID productId) {
        List<ReviewRow> reviews = loadReviews(productId);
        if (reviews.isEmpty()) {
            log.info("[ScoreService] product={} has no crawlable reviews; default score used", productId);
            return defaultScore(productId);
        }

        FeatureSummary features = calculateFeatures(reviews);
        var thresholds = thresholdProvider.get();
        var featureBands = thresholds.featurePercent;

        List<RuleEvidence> rules = buildRules(features, thresholds);

        double totalPenalty = rules.stream().mapToDouble(RuleEvidence::points).sum();
        double boundedPenalty = Math.min(100.0, Math.max(0.0, totalPenalty));
        double riskScore = Math.round(boundedPenalty * 10.0) / 10.0; // 0=良,100=悪
        double displayScore = Math.max(0.0, 100.0 - riskScore);      // UI向け

        String rank = rankForScore(riskScore);

        List<String> flags = new ArrayList<>();
        if (features.distBias() >= featureBands.dist_bias.warn) flags.add("ATTN_DISTRIBUTION");
        if (features.duplicateRatio() >= featureBands.duplicates.warn) flags.add("ATTN_DUPLICATE");
        if (features.surgeRatio() >= featureBands.surge.warn) flags.add("ATTN_SURGE");
        if (features.noiseRatio() >= featureBands.noise.warn) flags.add("ATTN_NOISE");

        SakuraLevel level = judgeSakuraLevel(features, thresholds);
        Map<String, Object> metrics = buildMetrics(features);

        return new ProductScore(
                productId,
                riskScore,
                displayScore,
                rank,
                level.name(),
                flags,
                rules,
                metrics
        );
    }

    private List<ReviewRow> loadReviews(UUID productId) {
        // posted_at を優先し、無ければ created_at を使う（サージ判定の精度向上）
        var sql = """
                SELECT COALESCE(stars, rating, 0) AS stars,
                       COALESCE(body, text, '') AS body,
                       COALESCE(posted_at, created_at) AS created_at,
                       fingerprint,
                       reviewer_meta AS reviewer_ref
                  FROM reviews
                 WHERE product_id = :productId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("productId", productId);
        try {
            return namedJdbcTemplate.query(sql, params, this::mapRow);
        } catch (DataAccessException e) {
            log.warn("[ScoreService] failed to load reviews for {}", productId, e);
            return List.of();
        }
    }

    private ReviewRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        int stars = Math.max(0, Math.min(5, rs.getInt("stars")));
        String body = OptionalString(rs.getString("body"));
        Timestamp created = rs.getTimestamp("created_at");
        Instant createdAt = created != null ? created.toInstant() : Instant.now();
        String fingerprint = OptionalString(rs.getString("fingerprint"));
        String reviewerRef = OptionalString(rs.getString("reviewer_ref"));
        String normalisedBody = OptionalString(TextNormalizer.normalize(body));
        return new ReviewRow(stars, body, createdAt, fingerprint, reviewerRef, normalisedBody);
    }

    private FeatureSummary calculateFeatures(List<ReviewRow> reviews) {
        int total = reviews.size();

        long fiveStar = reviews.stream().filter(r -> r.stars() >= 5).count();
        double distBias = percent(fiveStar, total);

        Map<String, Long> clusters = reviews.stream()
                .map(ReviewRow::clusterKey)
                .filter(StringUtils::hasText)
                .collect(groupingBy(s -> s, summingLong(v -> 1)));
        long maxCluster = clusters.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        double duplicates = percent(maxCluster, total);

        Map<LocalDate, Long> perDay = new LinkedHashMap<>();
        reviews.forEach(row -> {
            LocalDate day = LocalDate.ofInstant(row.createdAt(), UTC);
            perDay.merge(day, 1L, Long::sum);
        });
        long maxDayCount = perDay.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        int dayBuckets = perDay.size();
        double avgPerDay = dayBuckets == 0 ? 0.0 : (double) total / dayBuckets;
        double surge = avgPerDay <= 0 ? 0.0 : Math.min(100.0, (maxDayCount / avgPerDay) * 100.0);

        NoiseStats noiseStats = computeNoiseStats(reviews);
        double noise = Math.min(100.0, noiseStats.weightedScore());

        return new FeatureSummary(
                total,
                distBias,
                duplicates,
                surge,
                noise,
                fiveStar,
                maxCluster,
                maxDayCount,
                dayBuckets,
                avgPerDay,
                noiseStats
        );
    }

    private NoiseStats computeNoiseStats(List<ReviewRow> reviews) {
        if (reviews.isEmpty()) return new NoiseStats(0, 0, 0, 0, 0, 0);
        int urlHits = 0, emojiHits = 0, symbolRuns = 0, shortHits = 0, totalLength = 0;
        for (ReviewRow row : reviews) {
            String body = OptionalString(row.body());
            if (body.isEmpty()) { shortHits++; continue; }
            totalLength += body.length();
            if (URL_PATTERN.matcher(body).find()) urlHits++;
            if (containsEmoji(body)) emojiHits++;
            if (SYMBOL_RUN_PATTERN.matcher(body).find()) symbolRuns++;
            if (body.length() < SHORT_REVIEW_THRESHOLD) shortHits++;
        }
        double total = reviews.size();
        double urlRate = urlHits / total;
        double emojiRate = emojiHits / total;
        double symbolRate = symbolRuns / total;
        double shortRate = shortHits / total;

        double weightedScore = (urlRate * 0.4 + emojiRate * 0.2 + symbolRate * 0.2 + shortRate * 0.2) * 100.0;
        return new NoiseStats(urlHits, emojiHits, symbolRuns, shortHits, totalLength, weightedScore);
    }

    private List<RuleEvidence> buildRules(FeatureSummary features, ThresholdProvider.Thresholds thresholds) {
        var weights = thresholds.weights;
        var percentBands = thresholds.featurePercent;
        List<RuleEvidence> rules = new ArrayList<>(4);

        rules.add(rule("dist_bias", features.distBias(), percentBands.dist_bias.warn, percentBands.dist_bias.crit,
                weights.dist_bias, Map.of("five_star_count", features.fiveStarCount(), "total_reviews", features.totalReviews())));

        rules.add(rule("duplicates", features.duplicateRatio(), percentBands.duplicates.warn, percentBands.duplicates.crit,
                weights.duplicates, Map.of("max_cluster", features.maxDuplicateCluster(), "cluster_ratio", roundDouble(features.duplicateRatio()))));

        rules.add(rule("surge", features.surgeRatio(), percentBands.surge.warn, percentBands.surge.crit,
                weights.surge, Map.of("max_day_reviews", features.maxDayCount(), "days_tracked", features.dayBucketCount(), "avg_reviews_per_day", roundDouble(features.avgPerDay()))));

        NoiseStats noise = features.noiseStats();
        rules.add(rule("noise", features.noiseRatio(), percentBands.noise.warn, percentBands.noise.crit,
                weights.noise, Map.of("url_hits", noise.urlHits(), "emoji_hits", noise.emojiHits(), "symbol_runs", noise.symbolRuns(), "short_reviews", noise.shortReviews())));

        return rules;
    }

    private RuleEvidence rule(String id, double value, double warn, double crit, double weight, Map<String, Object> extra) {
        double normalized = Math.max(0.0, Math.min(1.0, value / 100.0));
        int points = (int) Math.round(normalized * weight * 100.0);
        return new RuleEvidence(id, value, warn, crit, weight, points, extra);
    }

    private SakuraLevel judgeSakuraLevel(FeatureSummary features, ThresholdProvider.Thresholds thresholds) {
        var sakuraPercent = thresholds.sakuraPercent;
        double dist = features.distBias();
        double dup = features.duplicateRatio();

        if (dist >= sakuraPercent.sakura.dist_bias && dup >= sakuraPercent.sakura.duplicates) return SakuraLevel.SAKURA;
        if (dist >= sakuraPercent.likely.dist_bias || dup >= sakuraPercent.likely.duplicates) return SakuraLevel.LIKELY;
        if (dist >= sakuraPercent.unlikely.dist_bias) return SakuraLevel.UNLIKELY;
        return SakuraLevel.GENUINE;
    }

    private Map<String, Object> buildMetrics(FeatureSummary features) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_reviews", features.totalReviews());
        metrics.put("dist_bias", roundDouble(features.distBias()));
        metrics.put("duplicate_ratio", roundDouble(features.duplicateRatio()));
        metrics.put("surge_ratio", roundDouble(features.surgeRatio()));
        metrics.put("noise_ratio", roundDouble(features.noiseRatio()));
        metrics.put("max_duplicate_cluster", features.maxDuplicateCluster());
        metrics.put("max_day_reviews", features.maxDayCount());
        metrics.put("day_buckets", features.dayBucketCount());
        metrics.put("avg_reviews_per_day", roundDouble(features.avgPerDay()));
        return metrics;
    }

    /** Persist inside an active Tx (caller ensures Tx). */
    private void persistScore(UUID productId,
                              double scoreValue,
                              String rank,
                              String sakuraJudge,
                              List<String> flags,
                              List<RuleEvidence> rules,
                              Map<String, Object> metrics) {
        ReviewScore.Id id = new ReviewScore.Id(productId, SCORE_SOURCE);
        ReviewScore entity = reviewScoreRepository.findById(id).orElseGet(() ->
                new ReviewScore(id, 0.0, rank, sakuraJudge, null, null, null, Instant.now(), Instant.now())
        );
        entity.setScore(scoreValue);
        entity.setRank(rank);
        entity.setSakuraJudge(sakuraJudge);
        entity.setFlags(toJsonNode(flags == null ? List.of() : flags));
        entity.setRules(toJsonNode(rules == null ? List.of() : rules));
        entity.setMetrics(toJsonNode(metrics == null ? Map.of() : metrics));
        entity.setComputedAt(Instant.now());
        reviewScoreRepository.saveAndFlush(entity);
    }

    private JsonNode toJsonNode(Object payload) {
        return objectMapper.valueToTree(payload);
    }

    /* --------------------------------------------------------------------- */
    /* Helpers                                                               */
    /* --------------------------------------------------------------------- */

    private double percent(double numerator, double denominator) {
        if (denominator <= 0) return 0.0;
        return (numerator / denominator) * 100.0;
    }

    private double roundDouble(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean containsEmoji(String text) {
        if (text == null || text.isBlank()) return false;
        return text.codePoints().anyMatch(cp -> {
            int type = Character.getType(cp);
            return type == Character.SURROGATE || type == Character.OTHER_SYMBOL || cp >= 0x1F000;
        });
    }

    private ProductScore defaultScore(UUID productId) {
        return new ProductScore(productId, 0.0, 100.0, "A", SakuraLevel.GENUINE.name(),
                List.of(), List.of(), Map.of());
    }

    private String rankForScore(double score) {
        if (score < 35.0d) return "A";
        if (score < 65.0d) return "B";
        return "C";
    }

    private static String OptionalString(String value) {
        return value == null ? "" : value.trim();
    }

    /* --------------------------------------------------------------------- */
    /* Records & DTOs                                                        */
    /* --------------------------------------------------------------------- */

    private record ReviewRow(int stars,
                             String body,
                             Instant createdAt,
                             String fingerprint,
                             String reviewerRef,
                             String normalizedBody) {
        String clusterKey() {
            if (StringUtils.hasText(fingerprint)) return fingerprint;
            if (StringUtils.hasText(reviewerRef)) return reviewerRef.toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(normalizedBody)) return normalizedBody;
            return null;
        }
    }

    private record FeatureSummary(int totalReviews,
                                  double distBias,
                                  double duplicateRatio,
                                  double surgeRatio,
                                  double noiseRatio,
                                  long fiveStarCount,
                                  long maxDuplicateCluster,
                                  long maxDayCount,
                                  int dayBucketCount,
                                  double avgPerDay,
                                  NoiseStats noiseStats) { }

    private record NoiseStats(int urlHits,
                              int emojiHits,
                              int symbolRuns,
                              int shortReviews,
                              int totalLength,
                              double weightedScore) { }

    public enum SakuraLevel { SAKURA, LIKELY, UNLIKELY, GENUINE }

    public record RuleEvidence(String id,
                               double value,
                               double warn,
                               double crit,
                               double weight,
                               int points,
                               Map<String, Object> extra) { }

    private record CacheEntry(ProductScore score, Instant expiresAt) { }

    public record ProductScore(UUID productId,
                               double score,
                               double displayScore,
                               String rank,
                               String sakuraJudge,
                               List<String> flags,
                               List<RuleEvidence> rules,
                               Map<String, Object> metrics) {
        public ProductScore {
            flags = CollectionUtils.isEmpty(flags) ? List.of() : List.copyOf(flags);
            rules = CollectionUtils.isEmpty(rules) ? List.of() : List.copyOf(rules);
            metrics = metrics == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
        }
    }
}
