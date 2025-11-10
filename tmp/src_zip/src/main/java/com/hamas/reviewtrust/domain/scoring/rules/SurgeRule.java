package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviewers who post an unusually high number of reviews in a 24‑hour
 * window. Sudden bursts of reviews can indicate coordinated review
 * manipulation or spam activity.
 */
public class SurgeRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        int reviews = ctx.getUserReviewsLast24h();
        int max = profile.getThresholds().maxReviewsPerDay;
        if (reviews > max) {
            double severity = (double) (reviews - max) / max;
            double penalty = severity * profile.weightFor(Flags.SURGE_ACTIVITY);
            String evidence = String.format("reviews24h=%d", reviews);
            return new RuleOutcome(Flags.SURGE_ACTIVITY, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.SURGE_ACTIVITY);
    }
}