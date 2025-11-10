// src/main/java/com/hamas/reviewtrust/domain/scoring/engine/ScoreService.java
package com.hamas.reviewtrust.domain.scoring.engine;

import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels.ScoreResult;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 旧コード互換の薄いFacade。
 * productId から ScoringContext を解決し、ScoringEngine でスコア計算して
 * 公開API用の ScoreModels.ScoreResult に変換して返す。
 */
@Service
public class ScoreService {

    private final ScoringEngine scoringEngine;          // ← 既存（s付き）
    private final ScoringContextResolver resolver;      // ← productId -> ScoringContext

    public ScoreService(ScoringEngine scoringEngine, ScoringContextResolver resolver) {
        this.scoringEngine = scoringEngine;
        this.resolver = resolver;
    }

    /** Controller 用：製品IDのスコアを算出（未解決なら empty） */
    public Optional<ScoreResult> computeForProduct(String productId) {
        ScoringContext ctx = resolver.resolveByProductId(productId);
        if (ctx == null) return Optional.empty();

        // 既存エンジンでスコア算出
        ScoringEngine.ScoreResult core = scoringEngine.score(ctx);

        // Ranker.Rank → ScoreModels.Rank へ変換（名前一致前提、壊れても安全側にC）
        ScoreModels.Rank rank;
        try {
            rank = ScoreModels.Rank.valueOf(core.getRank().name());
        } catch (Exception ignore) {
            rank = ScoreModels.Rank.C;
        }

        // ここでは最低限の出力に留める（必要になれば RuleOutcome → flags/rules/metrics を拡張）
        return Optional.of(new ScoreModels.ScoreResult(
                productId,
                core.getScore(),                 // 0..100（高い方が良）
                rank,                            // A/B/C
                ScoreModels.SakuraJudge.SAFE,    // 将来: プロファイル/ルールから判定を実装
                Map.of(),                        // metrics: 必要なら埋める
                List.of(),                       // flags:   必要なら RuleOutcome から生成
                List.of(),                       // rules:   必要なら RuleOutcome から生成
                Instant.now().toString()
        ));
    }

    /** productId をドメインの ScoringContext に解決するためのポート（実装は別コンポーネントで用意） */
    public interface ScoringContextResolver {
        ScoringContext resolveByProductId(String productId);
    }
}
