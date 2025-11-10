package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScoreReadService {

    private final ReviewScoreRepository repository;

    public ScoreReadService(ReviewScoreRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProductScores latestByProduct(UUID productId) {
        ScoreDetail amazon = toDetail(repository.findByIdProductIdAndIdSource(productId, "AMAZON"));
        ScoreDetail site = toDetail(repository.findByIdProductIdAndIdSource(productId, "SITE"));
        return new ProductScores(amazon, site);
    }

    private ScoreDetail toDetail(Optional<ReviewScore> optional) {
        return optional.map(score -> new ScoreDetail(
                score.getScore(),
                score.getRank(),
                score.getSakuraJudge(),
                score.getFlags(),
                score.getRules(),
                score.getMetrics(),
                score.getComputedAt()
        )).orElse(null);
    }

    public record ProductScores(ScoreDetail amazon, ScoreDetail site) {}

    public record ScoreDetail(double score,
                              String rank,
                              String sakuraJudge,
                              JsonNode flags,
                              JsonNode rules,
                              JsonNode metrics,
                              Instant computedAt) {}
}
