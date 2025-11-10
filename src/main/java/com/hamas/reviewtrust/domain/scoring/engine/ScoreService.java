package com.hamas.reviewtrust.domain.scoring.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.common.text.TextNormalizer;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.ScoreResult;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.SakuraJudge;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdProvider;
import com.hamas.reviewtrust.domain.scoring.rules.RuleEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes product scores by extracting aggregate review features, evaluating the {@link RuleEngine}
 * and storing the result in {@code review_scores}.
 */
@Service
public class ScoreService {

    private static final String SOURCE_AMAZON = "AMAZON";
    private static final int SURGE_WINDOW_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ThresholdProvider thresholdProvider;
    private final ObjectMapper mapper;
    private final RuleEngine ruleEngine = new RuleEngine();

    public ScoreService(JdbcTemplate jdbc,
                        NamedParameterJdbcTemplate namedJdbc,
                        ThresholdProvider thresholdProvider,
                        ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.thresholdProvider = thresholdProvider;
        this.mapper = mapper;
    }

    public Optional<ScoreResult> computeForProduct(String productId) {
        UUID pid = parseUuid(productId);
        if (pid == null) {
            return Optional.empty();
        }

        List<ReviewSample> reviews = loadReviews(pid);
        if (reviews.isEmpty()) {
            return Optional.empty();
        }

        FeatureVector features = computeFeatures(reviews);
        var thresholds = thresholdProvider.get();
        ScoreModels.FeatureSnapshot snapshot = features.toSnapshot();
        var evaluation = ruleEngine.evaluate(snapshot, thresholds);

        int score = Math.max(0, 100 - evaluation.total());
        ScoreModels.Rank rank = toRank(score);
        SakuraJudge judge = Ranker.judgeSakura(snapshot, thresholds);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_reviews", reviews.size());
        metrics.put("dist_bias", round(features.distBias()));
        metrics.put("duplicate_rate", round(features.duplicateRate()));
        metrics.put("surge_ratio", round(features.surgeRatio()));
        metrics.put("noise_ratio", round(features.noiseRatio()));
        metrics.put("recent_reviews", features.recentCount());
        metrics.put("window_days", SURGE_WINDOW_DAYS);

        persistScore(pid, score, rank, judge, metrics, evaluation.flags(), evaluation.rules());

        return Optional.of(new ScoreModels.ScoreResult(
                productId,
                score,
                rank,
                judge,
                metrics,
                evaluation.flags(),
                evaluation.rules(),
                Instant.now().toString()
        ));
    }

    /* ------------------------------------------------------------------ */
    /* Data access + feature engineering                                  */
    /* ------------------------------------------------------------------ */

    private List<ReviewSample> loadReviews(UUID productId) {
        return jdbc.query("""
                        SELECT rating,
                               COALESCE(body, '') AS body,
                               review_date,
                               fingerprint,
                               reviewer,
                               helpful_votes
                          FROM reviews
                         WHERE product_id = ?
                           AND source = ?
                        """,
                (rs, i) -> mapRow(rs),
                productId, SOURCE_AMAZON
        );
    }

    private FeatureVector computeFeatures(List<ReviewSample> reviews) {
        int total = reviews.size();
        double fiveStarRatio = reviews.stream().filter(r -> r.rating() >= 5).count() / (double) total;
        double shortTextRatio = reviews.stream()
                .filter(r -> r.normalisedBody() != null && r.normalisedBody().length() < 120)
                .count() / (double) total;
        double distBias = clamp01(fiveStarRatio * shortTextRatio);

        Map<String, Long> clusters = reviews.stream()
                .map(ReviewSample::clusterKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
        long maxCluster = clusters.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        double duplicateRate = clamp01(maxCluster / (double) total);

        LocalDate thresholdDate = LocalDate.now(ZoneId.systemDefault()).minusDays(SURGE_WINDOW_DAYS);
        long recent = reviews.stream()
                .filter(sample -> sample.reviewDate() != null && !sample.reviewDate().isBefore(thresholdDate))
                .count();
        double surgeRatio = clamp01((recent * 1.0) / Math.max(1.0, total / 5.0));

        double noiseRatio = clamp01(reviews.stream()
                .filter(sample -> isNoisy(sample.normalisedBody()))
                .count() / (double) total);

        return new FeatureVector(distBias, duplicateRate, surgeRatio, noiseRatio, (int) recent);
    }

    private boolean isNoisy(String body) {
        if (body == null || body.isBlank()) return true;
        if (body.length() < 40) return true;
        long uniqueChars = body.chars().distinct().count();
        return uniqueChars < 10;
    }

    private ReviewSample mapRow(ResultSet rs) throws SQLException {
        int rating = Math.max(0, Math.min(5, rs.getInt("rating")));
        String body = rs.getString("body");
        LocalDate reviewDate = rs.getObject("review_date") != null
                ? rs.getObject("review_date", LocalDate.class)
                : null;
        String normalisedBody = TextNormalizer.normalize(body);
        String fingerprint = rs.getString("fingerprint");
        String reviewer = rs.getString("reviewer");
        int helpful = rs.getInt("helpful_votes");
        return new ReviewSample(
                rating,
                normalisedBody,
                reviewDate,
                fingerprint,
                reviewer,
                helpful
        );
    }

    /* ------------------------------------------------------------------ */
    /* Persistence                                                        */
    /* ------------------------------------------------------------------ */

    private void persistScore(UUID productId,
                              int score,
                              ScoreModels.Rank rank,
                              SakuraJudge judge,
                              Map<String, Object> metrics,
                              List<String> flags,
                              List<ScoreModels.RuleDetail> rules) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("source", SOURCE_AMAZON)
                .addValue("score", score)
                .addValue("rank", rank.name())
                .addValue("judge", judge.name())
                .addValue("flags", toJsonString(flags))
                .addValue("rules", toJsonString(rules))
                .addValue("metrics", toJsonString(metrics));

        namedJdbc.update("""
                INSERT INTO review_scores
                   (product_id, source, score, rank, sakura_judge, flags, rules, metrics, computed_at, updated_at)
                VALUES
                   (:productId, :source, :score, :rank, :judge,
                    CAST(:flags AS jsonb), CAST(:rules AS jsonb), CAST(:metrics AS jsonb), now(), now())
                ON CONFLICT (product_id, source)
                DO UPDATE SET
                   score         = EXCLUDED.score,
                   rank          = EXCLUDED.rank,
                   sakura_judge  = EXCLUDED.sakura_judge,
                   flags         = EXCLUDED.flags,
                   rules         = EXCLUDED.rules,
                   metrics       = EXCLUDED.metrics,
                   computed_at   = EXCLUDED.computed_at,
                   updated_at    = now()
                """, params);
    }

    private String toJsonString(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise scoring payload", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Utility helpers                                                    */
    /* ------------------------------------------------------------------ */

    private UUID parseUuid(String productId) {
        try {
            return UUID.fromString(productId);
        } catch (Exception e) {
            return null;
        }
    }

    private ScoreModels.Rank toRank(int score) {
        return switch (Ranker.assign(score)) {
            case A -> ScoreModels.Rank.A;
            case B -> ScoreModels.Rank.B;
            case C -> ScoreModels.Rank.C;
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    /* ------------------------------------------------------------------ */
    /* Data holders                                                       */
    /* ------------------------------------------------------------------ */

    private record ReviewSample(
            int rating,
            String normalisedBody,
            LocalDate reviewDate,
            String fingerprint,
            String reviewer,
            int helpfulVotes
    ) {
        String clusterKey() {
            if (fingerprint != null && !fingerprint.isBlank()) return fingerprint;
            if (normalisedBody != null && !normalisedBody.isBlank()) return normalisedBody;
            return reviewer;
        }
    }

    private record FeatureVector(
            double distBias,
            double duplicateRate,
            double surgeRatio,
            double noiseRatio,
            int recentCount
    ) {
        ScoreModels.FeatureSnapshot toSnapshot() {
            return new ScoreModels.FeatureSnapshot(distBias, duplicateRate, surgeRatio, noiseRatio);
        }
    }
}
