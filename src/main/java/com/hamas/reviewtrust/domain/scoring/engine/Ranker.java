package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdProvider;

/**
 * Maps numeric scores and feature summaries into categorical ranks and sakura judgements.
 */
public final class Ranker {

    private Ranker() {
        // utility class
    }

    public enum Rank {
        A, B, C
    }

    /**
     * Assign rank (A best) based on the numeric score.
     */
    public static Rank assign(int score0to100) {
        int s = clamp(score0to100, 0, 100);
        if (s <= 34) return Rank.A;
        if (s <= 64) return Rank.B;
        return Rank.C;
    }

    public static ScoreModels.SakuraJudge judgeSakura(ScoreModels.FeatureSnapshot features,
                                                       ThresholdProvider.Thresholds thresholds) {
        double dist = clamp(features.distBias(), 0d, 1d);
        double dup = clamp(features.duplicateRate(), 0d, 1d);
        ThresholdProvider.Thresholds.Sakura sakura = thresholds.sakura;

        if (dist >= sakura.dist_bias_sakura && dup >= sakura.duplicate_sakura) {
            return ScoreModels.SakuraJudge.SAKURA;
        }
        if (dist >= sakura.dist_bias_likely || dup >= sakura.duplicate_likely) {
            return ScoreModels.SakuraJudge.LIKELY;
        }
        if (dist >= sakura.dist_bias_unlikely) {
            return ScoreModels.SakuraJudge.UNLIKELY;
        }
        return ScoreModels.SakuraJudge.GENUINE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
