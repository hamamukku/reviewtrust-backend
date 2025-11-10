package com.hamas.reviewtrust.domain.scoring.profile;

/**
 * Dimension categories used to group related scoring rules. Assigning rules
 * to dimensions allows weighting entire categories if desired (e.g. placing
 * more emphasis on reviewer behaviour versus content analysis).
 */
public enum Dimensions {
    /** Pertains to the content of the review text */
    CONTENT,
    /** Pertains to the reviewer's account and behaviour */
    USER,
    /** Pertains to rating patterns */
    RATING,
    /** Pertains to vendor or product distribution */
    VENDOR,
    /** Pertains to temporal activity patterns */
    ACTIVITY;
}