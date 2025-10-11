// ReviewDtos.java (placeholder)
package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.hamas.reviewtrust.domain.reviews.entity.Review;

import java.time.Instant;
import java.util.UUID;

/** 公開レビュー表示用DTO。最小限の項目のみ返す（本文・星・検証情報・作成時刻）。 */
public final class ReviewDtos {

    /** 一覧／表示用アイテム */
    public record ReviewItem(
            UUID reviewId,
            int stars,
            String text,
            boolean verifiedPurchase,
            boolean hasImage,
            String reviewerName,
            Boolean reviewerPublic,
            Instant createdAt
    ) {}

    /** エンティティ→DTO 変換 */
    public static ReviewItem from(Review r) {
        return new ReviewItem(
                r.getId(),
                r.getStars(),
                r.getText(),
                r.isVerifiedPurchase(),
                r.isHasImage(),
                r.getReviewerName(),
                r.getReviewerPublic(),
                r.getCreatedAt()
        );
    }

    private ReviewDtos() {}
}
