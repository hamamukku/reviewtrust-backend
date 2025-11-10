package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    Page<Review> findBySourceAndStatus(Review.Source source, Review.Status status, Pageable pageable);

    Page<Review> findByStatus(Review.Status status, Pageable pageable);

    Page<Review> findBySourceAndStatusIn(Review.Source source, Collection<Review.Status> statuses, Pageable pageable);

    Page<Review> findByStatusIn(Collection<Review.Status> statuses, Pageable pageable);

    Page<Review> findByProductIdAndStatus(UUID productId, Review.Status status, Pageable pageable);

    Page<Review> findByProductIdAndStatusIn(UUID productId, Collection<Review.Status> statuses, Pageable pageable);

    List<Review> findByProductId(UUID productId);

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    List<Review> findByProductIdOrderByCreatedAtDesc(UUID productId);

    long countByProductId(UUID productId);

    @Query("""
            select r from Review r
            where r.productId = :productId
              and (r.source = com.hamas.reviewtrust.domain.reviews.entity.Review$Source.AMAZON
                   or r.status = com.hamas.reviewtrust.domain.reviews.entity.Review$Status.APPROVED)
            """)
    Page<Review> findPublicReviews(@Param("productId") UUID productId, Pageable pageable);

    @Query("""
            select r from Review r
            where r.productId = :productId
              and r.source = com.hamas.reviewtrust.domain.reviews.entity.Review$Source.AMAZON
            order by coalesce(r.postedAt, r.createdAt) desc
            """)
    List<Review> findAmazonReviews(@Param("productId") UUID productId);

    @Query("""
            select r from Review r
            where r.productId = :productId
              and r.source = com.hamas.reviewtrust.domain.reviews.entity.Review$Source.USER
              and r.status = com.hamas.reviewtrust.domain.reviews.entity.Review$Status.APPROVED
            order by coalesce(r.postedAt, r.createdAt) desc
            """)
    List<Review> findApprovedUserReviews(@Param("productId") UUID productId);
}
