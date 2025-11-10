package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.Review;
import org.springframework.data.domain.Page;       // ← Page を使うので必須
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * ReviewService から呼ばれているメソッドのシグネチャ。
     * 例) repo.findByProductIdAndSourceAndStatus(productId, source, status, pageable)
     */
    Page<Review> findByProductIdAndSourceAndStatus(
            UUID productId,
            Review.Source source,
            Review.Status status,
            Pageable pageable
    );
}
