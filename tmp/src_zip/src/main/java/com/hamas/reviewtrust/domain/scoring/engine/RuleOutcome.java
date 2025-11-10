package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.profile.Flags;

/**
 * Encapsulates the result of applying a single scoring rule. A rule
 * contributes a penalty (zero if not triggered) and captures a human
 * readable evidence string explaining the outcome. The evidence may be
 * {@code null} when the rule does not apply.
 */
public final class RuleOutcome {
    private final Flags flag;
    private final double penalty;
    private final String evidence;

    public RuleOutcome(Flags flag, double penalty, String evidence) {
        this.flag = flag;
        this.penalty = penalty;
        this.evidence = evidence;
    }

    public Flags getFlag() { return flag; }
    public double getPenalty() { return penalty; }
    public String getEvidence() { return evidence; }
    public boolean isTriggered() { return penalty > 0; }

    /**
     * Factory for outcomes where the rule did not trigger.
     *
     * @param flag rule flag
     * @return outcome with zero penalty and no evidence
     */
    public static RuleOutcome noFinding(Flags flag) {
        return new RuleOutcome(flag, 0.0, null);
    }
}