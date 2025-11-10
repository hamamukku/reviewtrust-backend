package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviewers whose proportion of verified purchases is lower than the
 * configured threshold. A low verified purchase rate may indicate lack of
 * genuine buying behaviour.
 */
public class VerifiedPurchaseRateRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double rate = ctx.getUserVerifiedPurchaseRate();
        double threshold = profile.getThresholds().verifiedPurchaseRate;
        if (rate < threshold) {
            double severity = (threshold - rate) / threshold;
            double penalty = severity * profile.weightFor(Flags.LOW_VERIFIED_RATE);
            String evidence = String.format("verifiedRate=%.2f", rate);
            return new RuleOutcome(Flags.LOW_VERIFIED_RATE, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.LOW_VERIFIED_RATE);
    }
}