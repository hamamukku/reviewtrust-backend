package com.hamas.reviewtrust.domain.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity storing metadata about images attached to a review. Each
 * ReviewImage is associated with exactly one Review and records the
 * original path of the uploaded file along with basic dimensions and
 * size information. Images are immutable after creation.
 */
@Entity
@Table(name = "review_images",
       indexes = {
           @Index(name = "ix_review_images_review", columnList = "review_id")
       })
public class ReviewImage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Parent review. Many images can belong to one review. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    /** The storage path or key where the image is stored. */
    @Column(nullable = false, length = 2048)
    private String path;

    /** Image width in pixels. */
    @Column(nullable = false)
    private int width;

    /** Image height in pixels. */
    @Column(nullable = false)
    private int height;

    /** Total byte size of the image file. */
    @Column(nullable = false)
    private int bytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewImage() {
        // for JPA
    }

    private ReviewImage(UUID id, Review review, String path, int width, int height, int bytes, Instant createdAt) {
        this.id = id;
        this.review = review;
        this.path = path;
        this.width = width;
        this.height = height;
        this.bytes = bytes;
        this.createdAt = createdAt;
    }

    /**
     * Factory method to create a new ReviewImage. The identifier and
     * createdAt timestamp will be generated at persist time if null.
     *
     * @param review the parent review
     * @param path storage path
     * @param width image width in px
     * @param height image height in px
     * @param bytes file size in bytes
     * @return new ReviewImage instance
     */
    public static ReviewImage of(Review review, String path, int width, int height, int bytes) {
        return new ReviewImage(null, review, path, width, height, bytes, null);
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    // --- getters ---
    public UUID getId() { return id; }
    public Review getReview() { return review; }
    public String getPath() { return path; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getBytes() { return bytes; }
    public Instant getCreatedAt() { return createdAt; }
}