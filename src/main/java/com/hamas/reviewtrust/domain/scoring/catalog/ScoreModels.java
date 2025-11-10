// src/main/java/com/hamas/reviewtrust/domain/scoring/catalog/ScoreModels.java
package com.hamas.reviewtrust.domain.scoring.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ScoreModels {
    private ScoreModels(){}

    /** 入力特徴量（0..1の正規化、surgeはz値） */
    public record FeatureSnapshot(
            double distBias,
            double duplicateRate,
            double surgeZ,
            double noiseRate
    ) {}

    /** ランク（A:良 / C:悪） */
    public enum Rank { A, B, C }

    /** サクラ判定 */
    public enum SakuraJudge { GENUINE, UNLIKELY, LIKELY, SAKURA }

    /** ルール詳細（説明可能性のために返す） */
    public record RuleDetail(
            String rule,         // 例: "duplicates>=warn"
            double value,        // 実測値
            double warn,         // warn 閾値
            double weight,       // 重み
            int penalty          // このルールが寄与したペナルティ（0..100）
    ) {}

    /** 公開APIで返す結果 */
    public static final class ScoreResult {
        public final String productId;
        public final Integer score;           // 0..100
        public final Rank rank;               // A/B/C
        public final SakuraJudge sakuraJudge; // SAFE / LIKELY_SAKURA / SAKURA
        public final Map<String,Object> metrics;
        public final List<String> flags;      // ATTN_*
        public final List<RuleDetail> rules;  // ルール明細
        public final String computedAt;       // ISO
        public final Map<String,String> error;// 失敗時のみ

        public ScoreResult(String productId, Integer score, Rank rank, SakuraJudge sakuraJudge,
                           Map<String,Object> metrics, List<String> flags, List<RuleDetail> rules,
                           String computedAt) {
            this(productId, score, rank, sakuraJudge, metrics, flags, rules, computedAt, null);
        }

        public ScoreResult(String productId, Integer score, Rank rank, SakuraJudge sakuraJudge,
                           Map<String,Object> metrics, List<String> flags, List<RuleDetail> rules,
                           String computedAt, Map<String,String> error) {
            this.productId = productId;
            this.score = score;
            this.rank = rank;
            this.sakuraJudge = sakuraJudge;
            this.metrics = metrics;
            this.flags = flags;
            this.rules = rules;
            this.computedAt = computedAt;
            this.error = error;
        }
    }
}
