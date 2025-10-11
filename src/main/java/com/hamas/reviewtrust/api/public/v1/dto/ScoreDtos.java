// ScoreDtos.java (placeholder)
package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.hamas.reviewtrust.domain.reviews.service.ScoreReadService;

import java.time.Instant;

/**
 * /api/products/{id}/scores のDTO。
 * - ProductScoresDto: { amazon, user }
 * - ScoreDetail: { score, rank, flags, rules, breakdown, createdAt }
 */
public final class ScoreDtos {

    public record ProductScoresDto(
            ScoreDetail amazon,
            ScoreDetail user
    ) {}

    public record ScoreDetail(
            int score,
            String rank,         // "A"/"B"/"C"
            JsonNode flags,      // JSON（配列 or オブジェクト）
            JsonNode rules,      // JSON（ルール別内訳・evidenceを含み得る）
            JsonNode breakdown,  // JSON（将来の多次元）
            Instant createdAt
    ) {}

    // --- mapping ---

    public static ProductScoresDto from(ScoreReadService.ProductScores s) {
        return new ProductScoresDto(
                from(s.amazon()),
                from(s.user())
        );
    }

    public static ScoreDetail from(ScoreReadService.ScoreDetail d) {
        if (d == null) return null;
        return new ScoreDetail(
                d.score(),
                d.rank(),
                d.flags(),
                d.rules(),
                d.breakdown(),
                d.createdAt()
        );
    }

    private ScoreDtos() {}
}
