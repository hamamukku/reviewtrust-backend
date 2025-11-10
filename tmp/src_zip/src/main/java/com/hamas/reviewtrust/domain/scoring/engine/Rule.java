package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.profile.ScoringProfile;

/**
 * Defines the contract for a scoring rule. Implementations examine the
 * scoring context and produce a {@link RuleOutcome} indicating whether the
 * rule fired and how severe its contribution should be.
 */
public interface Rule {
    /**
     * Applies the rule to the provided context using thresholds and weights
     * from the supplied profile.
     *
     * @param ctx review context
     * @param profile scoring profile
     * @return outcome of the rule
     */
    RuleOutcome apply(ScoringContext ctx, ScoringProfile profile);
}