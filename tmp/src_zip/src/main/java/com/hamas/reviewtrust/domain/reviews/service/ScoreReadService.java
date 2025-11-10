// ScoreReadService.java (placeholder)
package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore.Scope;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 商品単位のスコア読み出し（二系統: AMAZON / SITE）。
 * ScoreController はこのサービスの戻りをそのままDTOへ写せば良い。
 */
@Service
public class ScoreReadService {

    private final ReviewScoreRepository scores;
    private final ObjectMapper om;

    public ScoreReadService(ReviewScoreRepository scores, ObjectMapper om) {
        this.scores = scores;
        this.om = om;
    }

    /** 最新の {amazon, user(site)} を返す。存在しない側は null。 */
    @Transactional(readOnly = true)
    public ProductScores latestByProduct(UUID productId) {
        ScoreDetail amazon = toDetail(scores.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(productId, Scope.AMAZON));
        ScoreDetail user   = toDetail(scores.findTop1ByProductIdAndScopeOrderByCreatedAtDesc(productId, Scope.SITE));
        return new ProductScores(amazon, user);
    }

    // --- helpers ---

    private ScoreDetail toDetail(Optional<ReviewScore> opt) {
        return opt.map(s -> new ScoreDetail(
                s.getScore(),
                s.getRank().name(),               // "A" | "B" | "C"
                readJson(s.getFlagsJson()),
                readJson(s.getRulesJson()),
                readJson(s.getBreakdownJson()),
                s.getCreatedAt()
        )).orElse(null);
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return om.readTree(json); }
        catch (Exception e) { return null; }
    }

    // 出力構造（Controller→DTOへの受け渡し用最小形）
    public record ProductScores(ScoreDetail amazon, ScoreDetail user) {}
    public record ScoreDetail(int score, String rank, JsonNode flags, JsonNode rules, JsonNode breakdown, Instant createdAt) {}
}

