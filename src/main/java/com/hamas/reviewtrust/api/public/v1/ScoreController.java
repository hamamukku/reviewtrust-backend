package com.hamas.reviewtrust.api.public.v1;

import com.hamas.reviewtrust.api.public.v1.dto.ScoreDtos; // ← DTO側のpackageも public.v1.dto に統一すること
import com.hamas.reviewtrust.domain.reviews.service.ScoreReadService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 公開スコアAPI
 * GET /api/products/{productId}/scores
 * - { amazon, user } 二系統の最新スコアを返す
 * - rules/flags/breakdown をJSONで透過
 */
@RestController
@RequestMapping("/api/products/{productId}/scores")
public class ScoreController {

    private final ScoreReadService scoreReads;

    public ScoreController(ScoreReadService scoreReads) {
        this.scoreReads = scoreReads;
    }

    @GetMapping
    public ScoreDtos.ProductScoresDto get(@PathVariable UUID productId) {
        var ps = scoreReads.latestByProduct(productId);
        return ScoreDtos.from(ps);
    }
}
