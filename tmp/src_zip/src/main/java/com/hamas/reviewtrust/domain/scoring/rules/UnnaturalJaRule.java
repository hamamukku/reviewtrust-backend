package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviews whose language appears unnatural or machine translated. A
 * higher unnatural language score indicates more unnatural language.
 */
public class UnnaturalJaRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double score = ctx.getUnnaturalLanguageScore();
        double threshold = profile.getThresholds().unnaturalLanguageScore;
        if (score > threshold) {
            double severity = (score - threshold) / (1.0 - threshold);
            double penalty = severity * profile.weightFor(Flags.UNNATURAL_LANGUAGE);
            String evidence = String.format("unnaturalScore=%.2f", score);
            return new RuleOutcome(Flags.UNNATURAL_LANGUAGE, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.UNNATURAL_LANGUAGE);
    }
}