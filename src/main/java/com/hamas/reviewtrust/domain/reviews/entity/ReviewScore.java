// ReviewScore.java (placeholder)
package com.hamas.reviewtrust.domain.reviews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * REVIEW_SCORES（MVP拡張版）
 * - scope: AMAZON | SITE（二系統スコア）
 * - score: 0..100、rank: A/B/C
 * - flags/rules/breakdown: JSON（evidence・内訳）
 * - productId で商品単位の最新スコアを引けるよう冗長保持
 *
 * 仕様整合:
 *  - 二系統スコア表示（商品詳細に amazonScore / userScore を併記） 
 *  - rules/evidence を公開APIで返却（ScoreControllerで使用）
 */
@Entity
@Table(name = "review_scores",
       indexes = {
           @Index(name = "ix_scores_product_scope_created", columnList = "product_id,scope,created_at"),
           @Index(name = "ix_scores_review", columnList = "review_id")
       })
public class ReviewScore {

    public enum Scope { AMAZON, SITE }
    public enum Rank  { A, B, C }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** 冗長保持：商品単位の最新スコア参照を高速化 */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** レビュー単位のスコアがある場合に関連付け（集約スコアではNULL可） */
    @Column(name = "review_id")
    private UUID reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Scope scope;

    @Column(nullable = false)
    private int score; // 0..100

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Rank rank; // A/B/C

    @Column(name = "flags", columnDefinition = "jsonb")
    private String flagsJson;

    @Column(name = "rules", columnDefinition = "jsonb")
    private String rulesJson;

    /** 将来：多次元（TRUST/EFFECT/...）の内訳 */
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private String breakdownJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewScore() { }

    private ReviewScore(UUID id, UUID productId, UUID reviewId, Scope scope, int score, Rank rank,
                        String flagsJson, String rulesJson, String breakdownJson, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.reviewId = reviewId;
        this.scope = scope;
        this.score = score;
        this.rank = rank;
        this.flagsJson = flagsJson;
        this.rulesJson = rulesJson;
        this.breakdownJson = breakdownJson;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    /** 商品単位の集約スコアを保存する際のファクトリ */
    public static ReviewScore ofProduct(UUID productId, Scope scope, int score, Rank rank,
                                        String flagsJson, String rulesJson, String breakdownJson) {
        return new ReviewScore(null, productId, null, scope, score, rank, flagsJson, rulesJson, breakdownJson, null);
    }

    // getters
    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getReviewId() { return reviewId; }
    public Scope getScope() { return scope; }
    public int getScore() { return score; }
    public Rank getRank() { return rank; }
    public String getFlagsJson() { return flagsJson; }
    public String getRulesJson() { return rulesJson; }
    public String getBreakdownJson() { return breakdownJson; }
    public Instant getCreatedAt() { return createdAt; }
}
