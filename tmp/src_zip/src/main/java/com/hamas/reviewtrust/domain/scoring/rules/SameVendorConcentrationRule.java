package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.engine.Rule;
import com.hamas.reviewtrust.domain.scoring.engine.RuleOutcome;
import com.hamas.reviewtrust.domain.scoring.engine.ScoringContext;
import com.hamas.reviewtrust.domain.scoring.profile.Flags;
import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Flags reviewers who predominantly post reviews for products from the same
 * vendor. Such concentrated reviewing patterns can indicate biased or paid
 * promotion.
 */
public class SameVendorConcentrationRule implements Rule {
    @Override
    public RuleOutcome apply(ScoringContext ctx, ScoringProfile profile) {
        double conc = ctx.getVendorConcentration();
        double threshold = profile.getThresholds().vendorConcentration;
        if (conc > threshold) {
            double severity = (conc - threshold) / (1.0 - threshold);
            double penalty = severity * profile.weightFor(Flags.SAME_VENDOR_CONCENTRATION);
            String evidence = String.format("vendorConcentration=%.2f", conc);
            return new RuleOutcome(Flags.SAME_VENDOR_CONCENTRATION, penalty, evidence);
        }
        return RuleOutcome.noFinding(Flags.SAME_VENDOR_CONCENTRATION);
    }
}