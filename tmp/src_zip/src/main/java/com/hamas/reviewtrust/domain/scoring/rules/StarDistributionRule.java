package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviewers whose distribution of star ratings exhibits high variance,
 * indicating potentially inconsistent or manipulative behaviour. The
 * variance should be provided in the context as a normalized value between
 * 0 and 1.
 */
public class StarDistributionRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double var = ctx.getStarDistributionVariance();
        double threshold = profile.getThresholds().starDistributionVariance;
        if (var > threshold) {
            double severity = (var - threshold) / (1.0 - threshold);
            double penalty = severity * profile.weightFor(Flags.STAR_DISTRIBUTION);
            String evidence = String.format("starVariance=%.2f", var);
            return new RuleOutcome(Flags.STAR_DISTRIBUTION, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.STAR_DISTRIBUTION);
    }
}