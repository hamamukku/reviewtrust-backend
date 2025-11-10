package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewScoreRepository extends JpaRepository<ReviewScore, ReviewScore.Id> {

    Optional<ReviewScore> findByIdProductIdAndIdSource(UUID productId, String source);
}
