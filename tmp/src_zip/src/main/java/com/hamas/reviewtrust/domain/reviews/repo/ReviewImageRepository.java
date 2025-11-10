package com.hamas.reviewtrust.domain.reviews.repo;

import com.hamas.reviewtrust.domain.reviews.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting and querying ReviewImage entities.
 */
@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {
    /**
     * Returns all images belonging to the specified review, ordered by creation time ascending.
     *
     * @param reviewId review identifier
     * @return list of images
     */
    List<ReviewImage> findByReview_IdOrderByCreatedAtAsc(UUID reviewId);
}