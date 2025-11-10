// ReviewScoreRepository.java (placeholder)
package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore;
import com.hamas.reviewtrust.domain.reviews.entity.ReviewScore.Scope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewScoreRepository extends JpaRepository<ReviewScore, UUID> {

    /** 商品×スコープの最新スコア（created_at 降順で先頭） */
    Optional<ReviewScore> findTop1ByProductIdAndScopeOrderByCreatedAtDesc(UUID productId, Scope scope);
}

