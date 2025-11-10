package com.hamas.reviewtrust.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.catalog.RuleCatalogService;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringEngine;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.Ranker;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScoringEngine verifying score aggregation and rank assignment.
 */
public class ScoringEngineTest {

    @Test
    void scoreHighQualityReviewProducesHighScore() {
        ScoringEngine engine = new ScoringEngine(new RuleCatalogService(), new ScoringProfileRepository());
        ScoringContext ctx = new ScoringContext.Builder()
                .starRating(5)
                .verifiedPurchase(true)
                .specificityScore(1.0)
                .userVerifiedPurchaseRate(1.0)
                .userAccountAgeDays(365)
                .userReviewsLast24h(0)
                .vendorConcentration(0.0)
                .noiseRatio(0.0)
                .unnaturalLanguageScore(0.0)
                .duplicateTextSimilarity(0.0)
                .build();
        ScoringEngine.ScoreResult result = engine.score(ctx);
        assertTrue(result.getScore() > 90, "Expected high score for clean review");
        assertEquals(0, result.getOutcomes().size(), "No rule should trigger");
        assertEquals(Ranker.Rank.C, result.getRank(), "High scores map to rank C");
    }

    @Test
    void scoreSuspiciousReviewTriggersMultipleRules() {
        ScoringEngine engine = new ScoringEngine(new RuleCatalogService(), new ScoringProfileRepository());
        ScoringContext ctx = new ScoringContext.Builder()
                .starRating(5)
                .verifiedPurchase(false)
                .specificityScore(0.0)
                .userVerifiedPurchaseRate(0.0)
                .userAccountAgeDays(0)
                .userReviewsLast24h(50)
                .vendorConcentration(1.0)
                .noiseRatio(1.0)
                .unnaturalLanguageScore(1.0)
                .duplicateTextSimilarity(1.0)
                .build();
        ScoringEngine.ScoreResult result = engine.score(ctx);
        assertTrue(result.getScore() < 50, "Low score expected for highly suspicious review");
        List<RuleOutcome> outs = result.getOutcomes();
        assertFalse(outs.isEmpty(), "Multiple rules should trigger");
        // Ensure that at least duplicate text and low specificity flags appear
        boolean hasDuplicate = outs.stream().anyMatch(o -> o.getFlag().name().equals("DUPLICATE_TEXT"));
        boolean hasSpecificity = outs.stream().anyMatch(o -> o.getFlag().name().equals("LOW_SPECIFICITY"));
        assertTrue(hasDuplicate && hasSpecificity);
    }
}