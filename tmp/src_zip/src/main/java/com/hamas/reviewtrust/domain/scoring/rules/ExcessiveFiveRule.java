package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviews that give a five‑star rating during a period of high activity
 * from the same reviewer. A stream of five‑star reviews may indicate
 * inauthentic rating behaviour.
 */
public class ExcessiveFiveRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        int star = ctx.getStarRating();
        int reviews = ctx.getUserReviewsLast24h();
        int threshold = profile.getThresholds().maxReviewsPerDay;
        // Trigger when review is 5 stars and reviewer is very active
        if (star >= 5 && reviews > (threshold * 0.6)) {
            double severity = (double) (reviews - (int) (threshold * 0.6)) / (threshold * 0.4);
            double penalty = severity * profile.weightFor(Flags.EXCESSIVE_FIVE_STAR);
            String evidence = String.format("reviews24h=%d", reviews);
            return new RuleOutcome(Flags.EXCESSIVE_FIVE_STAR, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.EXCESSIVE_FIVE_STAR);
    }
}