package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Penalises reviews whose specificity score falls below a configured
 * threshold. Specificity reflects how detailed or informative a review
 * is—low specificity often indicates vague or unhelpful feedback typical
 * of fake or spam reviews. A lower specificityScore yields a higher
 * penalty relative to the configured threshold.
 */
public class SpecificityRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double score = ctx.getSpecificityScore();
        double threshold = profile.getThresholds().specificityScore;
        if (score < threshold) {
            // Compute severity as the proportion the score falls below the threshold.
            double severity = (threshold - score) / threshold;
            // Apply the configured weight to determine the penalty.
            double penalty = severity * profile.weightFor(Flags.LOW_SPECIFICITY);
            String evidence = String.format("specificityScore=%.2f", score);
            return new RuleOutcome(Flags.LOW_SPECIFICITY, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.LOW_SPECIFICITY);
    }
}