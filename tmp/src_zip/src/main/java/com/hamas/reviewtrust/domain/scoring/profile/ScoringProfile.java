package com.hamas.reviewtrust.domain.scoring.profile;

import java.util.EnumMap;
import java.util.Map;

/**
 * A configuration container describing how each scoring rule contributes to the
 * final penalty. Each rule has an associated weight that scales the severity
 * of its contribution. Thresholds can be tuned independently to change
 * sensitivity without adjusting weight.
 */
public class ScoringProfile {
    private final Thresholds thresholds;
    private final Map<Flags, Double> weights;

    public ScoringProfile() {
        this.thresholds = new Thresholds();
        this.weights = new EnumMap<>(Flags.class);
        // default weights (higher means greater impact on the final score)
        weights.put(Flags.DUPLICATE_TEXT, 15.0);
        weights.put(Flags.UNNATURAL_LANGUAGE, 5.0);
        weights.put(Flags.STAR_DISTRIBUTION, 5.0);
        weights.put(Flags.EXCESSIVE_FIVE_STAR, 5.0);
        weights.put(Flags.SURGE_ACTIVITY, 10.0);
        weights.put(Flags.NOISE, 3.0);
        weights.put(Flags.LOW_VERIFIED_RATE, 5.0);
        weights.put(Flags.ANONYMOUS_REVIEWER, 5.0);
        weights.put(Flags.NEW_ACCOUNT_BURST, 10.0);
        weights.put(Flags.SAME_VENDOR_CONCENTRATION, 5.0);
        weights.put(Flags.LOW_SPECIFICITY, 5.0);
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    /**
     * Returns the penalty weight associated with a particular flag. If the
     * weight is undefined it defaults to zero.
     *
     * @param flag rule flag
     * @return penalty weight
     */
    public double weightFor(Flags flag) {
        return weights.getOrDefault(flag, 0.0);
    }

    /**
     * Allows overriding the weight for a given flag.
     *
     * @param flag rule flag
     * @param weight new weight value (must be non‑negative)
     */
    public void setWeight(Flags flag, double weight) {
        if (weight < 0) throw new IllegalArgumentException("weight must be >= 0");
        weights.put(flag, weight);
    }
}