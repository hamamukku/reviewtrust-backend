package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags new accounts that post many reviews in a short period of time. New
 * accounts are defined by their age in days and number of reviews required
 * before flagging are configurable in the thresholds.
 */
public class NewAccountBurstRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        int age = ctx.getUserAccountAgeDays();
        int reviews = ctx.getUserReviewsLast24h();
        int ageLimit = profile.getThresholds().newAccountAgeDays;
        int burstReviews = profile.getThresholds().newAccountBurstReviews;
        if (age < ageLimit && reviews > burstReviews) {
            double severity = (double) (reviews - burstReviews) / Math.max(1, burstReviews);
            double penalty = severity * profile.weightFor(Flags.NEW_ACCOUNT_BURST);
            String evidence = String.format("newAccountAge=%d,reviews24h=%d", age, reviews);
            return new RuleOutcome(Flags.NEW_ACCOUNT_BURST, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.NEW_ACCOUNT_BURST);
    }
}