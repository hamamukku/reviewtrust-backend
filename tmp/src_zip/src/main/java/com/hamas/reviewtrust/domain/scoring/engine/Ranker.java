package com.hamas.reviewtrust.domain.scoring.engine;

/**
 * Maps a numeric score in the range 0–100 to a categorical rank. The
 * boundaries follow the specification: A = 0–34, B = 35–64, C = 65–100.
 */
public final class Ranker {
    /** Categorical rank codes. */
    public enum Rank {
        A, B, C
    }

    private Ranker() {
        // utility class
    }

    /**
     * Assigns a rank based on the provided score. Scores outside the
     * 0–100 range are clamped.
     *
     * @param score0to100 raw score
     * @return rank category
     */
    public static Rank assign(int score0to100) {
        int s = clamp(score0to100, 0, 100);
        if (s <= 34) return Rank.A;
        if (s <= 64) return Rank.B;
        return Rank.C;
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }
}