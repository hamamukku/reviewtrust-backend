package com.hamas.reviewtrust.domain.scoring.profile;

/**
 * Enumeration of heuristic flags produced by scoring rules. Each flag
 * represents a suspicious pattern observed in a review or reviewer.
 */
public enum Flags {
    /** Review text is extremely similar to another review */
    DUPLICATE_TEXT,
    /** Language appears machine‑translated or unnatural */
    UNNATURAL_LANGUAGE,
    /** The distribution of star ratings is skewed */
    STAR_DISTRIBUTION,
    /** Disproportionately many five‑star ratings */
    EXCESSIVE_FIVE_STAR,
    /** Sudden surge of reviews in a short time frame */
    SURGE_ACTIVITY,
    /** Text contains a high ratio of noise (e.g. gibberish) */
    NOISE,
    /** Reviewer rarely has verified purchases */
    LOW_VERIFIED_RATE,
    /** Reviewer hides their identity */
    ANONYMOUS_REVIEWER,
    /** New account posting many reviews quickly */
    NEW_ACCOUNT_BURST,
    /** Reviewer focuses disproportionately on the same vendor */
    SAME_VENDOR_CONCENTRATION,
    /** Review lacks specific information */
    LOW_SPECIFICITY;
}