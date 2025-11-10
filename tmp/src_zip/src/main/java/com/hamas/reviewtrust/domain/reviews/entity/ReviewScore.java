package com.hamas.reviewtrust.domain.reviews.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import java.time.Instant;
import java.util.Map;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    /** 0..100の実数スコア */
    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Rank rank; // A/B/C

    /** 判定ラベル（SAKURA, LIKELY, UNLIKELY, GENUINE） */
    @Column(name = "sakura_judge", nullable = false, length = 32)
    private String sakuraJudge;

    /** heuristicフラグ */
    @Type(org.hibernate.type.JsonType.class)
    @Column(name = "flags", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> flags;

    /** ルール詳細 */
    @Type(org.hibernate.type.JsonType.class)
    @Column(name = "rules", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rules;

    /** 将来：多次元（TRUST/EFFECT/...）の内訳 */
    @Type(org.hibernate.type.JsonType.class)
    @Column(name = "breakdown", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> breakdown;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    /** 商品単位の集約スコアを保存する際のファクトリ */
    public static ReviewScore ofProduct(UUID productId,
                                        Scope scope,
                                        double score,
                                        Rank rank,
                                        String sakuraJudge,
                                        Map<String, Object> flags,
                                        Map<String, Object> rules,
                                        Map<String, Object> breakdown) {
        ReviewScore r = new ReviewScore();
        r.productId = productId;
        r.scope = scope;
        r.score = score;
        r.rank = rank;
        r.sakuraJudge = sakuraJudge;
        r.flags = flags;
        r.rules = rules;
        r.breakdown = breakdown;
        return r;
    }
}
