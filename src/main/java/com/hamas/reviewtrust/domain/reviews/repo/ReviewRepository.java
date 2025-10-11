// ReviewRepository.java (placeholder)
package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.entity.Review.Source;
import com.hamas.reviewtrust.domain.reviews.entity.Review.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /** 公開一覧（新しい順） */
    Page<Review> findByProductIdAndSourceAndStatus(
            UUID productId, Source source, Status status, Pageable pageable
    );

    /** 承認キュー（古い順）は Pageable + Sort.ASC(createdAt) を使用 */
}
