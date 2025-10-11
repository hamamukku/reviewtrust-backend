// Ranker.java (placeholder)
package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;

/**
 * ランク割当（A/B/C）。
 * 仕様の既定境界: A=0–34, B=35–64, C=65–100
 * 入力スコアは 0..100 にクリップして判定する。
 */
public final class Ranker {

    private Ranker() {}

    public static ReviewScore.Rank assign(int score0to100) {
        int s = clamp(score0to100, 0, 100);
        if (s <= 34) return ReviewScore.Rank.A;
        if (s <= 64) return ReviewScore.Rank.B;
        return ReviewScore.Rank.C;
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }
}
