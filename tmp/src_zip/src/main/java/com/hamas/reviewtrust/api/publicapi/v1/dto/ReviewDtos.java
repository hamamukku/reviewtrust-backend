package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hamas.reviewtrust.domain.reviews.entity.Review;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 公開レビュー表示用DTO。最小限の項目のみ返す（本文・星・検証情報・作成時刻）。 */
public final class ReviewDtos {

    /** 一覧／表示用アイテム（null はシリアライズ時に省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    /** エンティティ→DTO 変換（reviewerPublic=false の場合は reviewerName を隠す） */
    public static ReviewItem from(Review r) {
        final boolean isPublic = Boolean.TRUE.equals(r.getReviewerPublic());
        final String safeName = isPublic ? r.getReviewerName() : null;
        return new ReviewItem(
                r.getId(),
                r.getStars(),
                r.getText(),
                r.isVerifiedPurchase(),
                r.isHasImage(),
                safeName,
                r.getReviewerPublic(),
                r.getCreatedAt()
        );
    }

    /** コレクション変換のユーティリティ（Controllerで使い勝手向上） */
    public static List<ReviewItem> fromAll(Collection<Review> reviews) {
        return reviews.stream().map(ReviewDtos::from).toList();
    }

    private ReviewDtos() {}
}

