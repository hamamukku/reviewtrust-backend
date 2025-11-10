// src/main/java/com/hamas/reviewtrust/domain/scoring/engine/ScoringEngine.java
package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.catalog.RuleCatalogService;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfileRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Central orchestrator that applies scoring rules to a review context,
 * aggregates penalties, assigns a rank, and exposes evidence for the
 * triggering rules.
 *
 * <p>Score range: <b>0..100 (0 = worst, 100 = best)</b>.
 * Each rule returns a non-negative penalty; the final score is
 * <code>100 - sum(penalties)</code>, clamped to [0,100].</p>
 */
@Service // ← DI できるように Bean 化（ScoreEngine アダプタ等から注入される）
public class ScoringEngine {

    private final RuleCatalogService catalog;
    private final ScoringProfileRepository profileRepo;

    public ScoringEngine(RuleCatalogService catalog, ScoringProfileRepository profileRepo) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.profileRepo = Objects.requireNonNull(profileRepo, "profileRepo");
    }

    /** Immutable scoring result. */
    public static final class ScoreResult {
        private final int score;                   // 0..100 (higher is better)
        private final Ranker.Rank rank;            // A/B/C (depends on Ranker.assign)
        private final List<RuleOutcome> outcomes;  // triggered rule outcomes only

        public ScoreResult(int score, Ranker.Rank rank, List<RuleOutcome> outcomes) {
            this.score = score;
            this.rank = Objects.requireNonNull(rank, "rank");
            this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
        }

        public int getScore() { return score; }
        public Ranker.Rank getRank() { return rank; }
        public List<RuleOutcome> getOutcomes() { return outcomes; }
    }

    /**
     * Apply all rules in the catalog to the given context using the active {@link ScoringProfile}.
     * <ul>
     *   <li>Accumulates non-negative penalties from triggered rules only.</li>
     *   <li>Final score = {@code clamp(100 - totalPenalty, 0, 100)}.</li>
     *   <li>Rank is assigned by {@link Ranker#assign(int)} (expects 0..100 where higher is better).</li>
     * </ul>
     */
    public ScoreResult score(ScoringContext ctx) {
        ScoringProfile profile = Objects.requireNonNull(profileRepo.get(), "scoring profile");

        double totalPenalty = 0.0;
        List<RuleOutcome> triggered = new ArrayList<>();

        // Evaluate all rules and accumulate penalties for the ones that triggered.
        for (Rule rule : catalog.getRules()) {
            RuleOutcome outcome = rule.apply(ctx, profile);
            if (outcome != null && outcome.isTriggered()) {
                // guard against negative/NaN/INF penalties from rule implementations
                double p = sanitizePenalty(outcome.getPenalty());
                totalPenalty += p;
                triggered.add(outcome);
            }
        }

        // 100 - sum(penalties), clamped to [0,100]
        int rawScore = (int) Math.round(clamp(100.0 - totalPenalty, 0.0, 100.0));
        Ranker.Rank rank = Ranker.assign(rawScore);

        return new ScoreResult(rawScore, rank, triggered);
    }

    /** ペナルティの健全化（負値・NaN・Infinity を 0 に丸める） */
    private static double sanitizePenalty(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.max(0.0, v);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
