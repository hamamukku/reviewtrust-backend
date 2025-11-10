package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviews whose text is highly similar to other reviews from the same
 * user. The similarity is provided by the scoring context and compared
 * against the duplicate text threshold. The penalty scales linearly with
 * similarity above the threshold.
 */
public class DuplicateTextRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double sim = ctx.getDuplicateTextSimilarity();
        double threshold = profile.getThresholds().duplicateTextSimilarity;
        if (sim > threshold) {
            double severity = (sim - threshold) / (1.0 - threshold);
            double penalty = severity * profile.weightFor(Flags.DUPLICATE_TEXT);
            String evidence = String.format("duplicate similarity=%.2f", sim);
            return new RuleOutcome(Flags.DUPLICATE_TEXT, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.DUPLICATE_TEXT);
    }
}