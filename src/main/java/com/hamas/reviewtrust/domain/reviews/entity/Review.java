package com.hamas.reviewtrust.domain.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * REVIEWS
 * - source: AMAZON | SITE
 * - status: DRAFT | PUBLISHED | REJECTED
 * - stars(1..5), text, verifiedPurchase, hasImage
 * - reviewerName, reviewerPublic(Boolean), reviewerMeta(jsonb)
 * - proofImagePath: SITE投稿の購入証明（必須）
 *
 * 仕様：MVP要件の投稿フロー/購入証明必須に準拠。 
 */
@Entity
@Table(name = "reviews",
       indexes = {
           @Index(name = "ix_reviews_product_status", columnList = "product_id,status"),
           @Index(name = "ix_reviews_product_src_status", columnList = "product_id,source,status"),
           @Index(name = "ix_reviews_created_at", columnList = "created_at")
       })
public class Review {

    public enum Source { AMAZON, SITE, USER }
    public enum Status { DRAFT, PUBLISHED, REJECTED, PENDING, APPROVED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "stars")
    private Integer stars;

    @Column(name = "text", columnDefinition = "text", nullable = false)
    private String text;

    @Column(name = "title")
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Column(name = "has_image", nullable = false)
    private boolean hasImage;

    /** 任意のユーザー識別（SITE投稿で使う場合がある） */
    @Column(name = "user_ref")
    private String userRef;

    @Column(name = "reviewer_name")
    private String reviewerName;

    /** 公開可否（SITE投稿のニックネーム露出など、nullは不明） */
    @Column(name = "reviewer_public")
    private Boolean reviewerPublic;

    /** 追加属性（JSON文字列） */
    @Column(name = "reviewer_meta", columnDefinition = "jsonb")
    private String reviewerMetaJson;

    /** 購入証明の保存パス（SITE/USER投稿向け） */
    @Column(name = "proof_image_path")
    private String proofUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() { }

    private Review(UUID id, UUID productId, Source source, Status status, Integer stars, String text,
                   boolean verifiedPurchase, boolean hasImage, String userRef, String reviewerName,
                   Boolean reviewerPublic, String reviewerMetaJson, String proofUrl,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.source = source;
        this.status = status;
        this.stars = stars;
        this.text = text;
        this.title = null;
        this.body = text;
        this.postedAt = null;
        this.verifiedPurchase = verifiedPurchase;
        this.hasImage = hasImage;
        this.userRef = userRef;
        this.reviewerName = reviewerName;
        this.reviewerPublic = reviewerPublic;
        this.reviewerMetaJson = reviewerMetaJson;
        this.proofUrl = proofUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** SITE投稿のドラフト作成（購入証明必須・verifiedPurchase/hasImage=true） */
    public static Review siteDraft(UUID productId, int stars, String text, String proofImagePath,
                                   String reviewerName, Boolean reviewerPublic, String userRef) {
        return new Review(
                null, productId, Source.SITE, Status.PENDING, stars, text,
                true, true, userRef, reviewerName, reviewerPublic, null,
                proofImagePath, null, null
        );
    }

    /** USER投稿（proofは任意）を作成 */
    public static Review userSubmission(UUID productId, int stars, String title, String body,
                                        boolean hasProof, String proofUrl, Instant clockNow) {
        Review review = new Review();
        review.productId = productId;
        review.source = Source.USER;
        review.status = Status.PENDING;
        review.stars = Integer.valueOf(stars);
        review.title = title;
        review.body = body;
        review.text = body;
        review.postedAt = clockNow;
        review.verifiedPurchase = hasProof;
        review.hasImage = hasProof;
        review.proofUrl = proofUrl;
        review.createdAt = clockNow;
        review.updatedAt = clockNow;
        return review;
    }

    // --- getters / setters ---
    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public Source getSource() { return source; }
    public Status getStatus() { return status; }
    public Integer getStars() { return stars; }
    public String getText() { return text; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getPostedAt() { return postedAt; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public boolean isHasImage() { return hasImage; }
    public String getUserRef() { return userRef; }
    public String getReviewerName() { return reviewerName; }
    public Boolean getReviewerPublic() { return reviewerPublic; }
    public String getReviewerMetaJson() { return reviewerMetaJson; }
    public String getProofUrl() { return proofUrl; }
    @Deprecated
    public String getProofImagePath() { return proofUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setReviewerMetaJson(String reviewerMetaJson) { this.reviewerMetaJson = reviewerMetaJson; }

    public void setStars(Integer stars) { this.stars = stars; }
    public void setStars(int stars) { this.stars = Integer.valueOf(stars); }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public void setProofUrl(String proofUrl) { this.proofUrl = proofUrl; }
    public void setSource(Source source) { this.source = source; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public void setVerifiedPurchase(boolean verifiedPurchase) { this.verifiedPurchase = verifiedPurchase; }
    public void setHasImage(boolean hasImage) { this.hasImage = hasImage; }
    public void setText(String text) { this.text = text; }
}

