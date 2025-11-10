// src/main/java/com/hamas/reviewtrust/domain/scoring/rules/RuleEngine.java
package com.hamas.reviewtrust.domain.scoring.rules;

import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.FeatureSnapshot;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.RuleDetail;
import com.hamas.reviewtrust.domain.scoring.profile.ThresholdsLoader.Thresholds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuleEngine {

    public static final String FLAG_DIST  = "ATTN_DISTRIBUTION";
    public static final String FLAG_DUP   = "ATTN_DUPLICATE";
    public static final String FLAG_SURGE = "ATTN_SURGE";
    public static final String FLAG_NOISE = "ATTN_NOISE";

    /** ルール評価とペナルティ寄与の内訳を返す */
    public EvalResult evaluate(FeatureSnapshot f, Thresholds th){
        List<String> flags = new ArrayList<>();
        List<RuleDetail> details = new ArrayList<>();

        // dist_bias
        double pDist = clamp01(f.distBias()) * 100.0 * th.weights.dist_bias;
        if (f.distBias() >= th.dist_bias.warn) {
            flags.add(FLAG_DIST);
            details.add(new RuleDetail("dist_bias>=warn", f.distBias(), th.dist_bias.warn, th.weights.dist_bias, (int)Math.round(pDist)));
        }

        // duplicates
        double pDup = clamp01(f.duplicateRate()) * 100.0 * th.weights.duplicates;
        if (f.duplicateRate() >= th.duplicates.warn) {
            flags.add(FLAG_DUP);
            details.add(new RuleDetail("duplicates>=warn", f.duplicateRate(), th.duplicates.warn, th.weights.duplicates, (int)Math.round(pDup)));
        }

        // surge (z→0..1正規化)
        double surge01 = normalizeZ(f.surgeZ(), th.surge_z.warn, th.surge_z.crit);
        double pSurge  = clamp01(surge01) * 100.0 * th.weights.surge;
        if (f.surgeZ() >= th.surge_z.warn) {
            flags.add(FLAG_SURGE);
            details.add(new RuleDetail("surge_z>=warn", f.surgeZ(), th.surge_z.warn, th.weights.surge, (int)Math.round(pSurge)));
        }

        // noise
        double pNoise = clamp01(f.noiseRate()) * 100.0 * th.weights.noise;
        if (f.noiseRate() >= th.noise.warn) {
            flags.add(FLAG_NOISE);
            details.add(new RuleDetail("noise>=warn", f.noiseRate(), th.noise.warn, th.weights.noise, (int)Math.round(pNoise)));
        }

        int total = (int)Math.round(Math.min(100.0, pDist + pDup + pSurge + pNoise));

        return new EvalResult(total, flags, details, Map.of(
                "pDist", pDist, "pDup", pDup, "pSurge", pSurge, "pNoise", pNoise
        ));
    }

    private static double clamp01(double v){ return Math.max(0d, Math.min(1d, v)); }
    private static double normalizeZ(double z, double warn, double crit) {
        if (crit <= warn) return (z >= crit) ? 1.0 : 0.0;
        return (z - warn) / (crit - warn);
    }

    public record EvalResult(int total, List<String> flags, List<RuleDetail> rules, Map<String,Double> penalties){}
}
