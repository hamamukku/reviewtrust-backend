package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags anonymous reviewers, particularly when the account is relatively new.
 */
public class ReviewerAnonymousRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        boolean anon = ctx.isAnonymous();
        int age = ctx.getUserAccountAgeDays();
        int threshold = profile.getThresholds().accountAgeDays;
        if (anon && age < threshold) {
            double severity = (double) (threshold - age) / threshold;
            double penalty = severity * profile.weightFor(Flags.ANONYMOUS_REVIEWER);
            String evidence = String.format("accountAgeDays=%d", age);
            return new RuleOutcome(Flags.ANONYMOUS_REVIEWER, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.ANONYMOUS_REVIEWER);
    }
}