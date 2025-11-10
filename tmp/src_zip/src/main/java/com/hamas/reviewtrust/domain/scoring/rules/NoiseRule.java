package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviews containing a high proportion of noise characters. Noise could
 * be random symbols, excessive punctuation or gibberish often found in fake
 * reviews. The noise ratio should be provided by upstream text analysis.
 */
public class NoiseRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double ratio = ctx.getNoiseRatio();
        double threshold = profile.getThresholds().noiseRatio;
        if (ratio > threshold) {
            double severity = (ratio - threshold) / (1.0 - threshold);
            double penalty = severity * profile.weightFor(Flags.NOISE);
            String evidence = String.format("noiseRatio=%.2f", ratio);
            return new RuleOutcome(Flags.NOISE, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.NOISE);
    }
}