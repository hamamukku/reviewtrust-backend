// ProductTag.java (placeholder)
package com.hamas.reviewtrust.domain.products.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PRODUCT_TAGS（多対多の連結テーブル）
 * - 主キー: (product_id, tag_id)
 * - 参照: products(id), tags(id)
 * ファイルツリーの設計通り、カテゴリ1段のタグ付けに使用。 
 */
@Entity
@Table(name = "product_tags",
       uniqueConstraints = @UniqueConstraint(name = "uk_product_tags_pid_tid", columnNames = {"product_id","tag_id"}))
@IdClass(ProductTag.PK.class)
public class ProductTag {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Id
    @Column(name = "tag_id", nullable = false, updatable = false)
    private UUID tagId;

    /** 参照の便宜（書込みは productId/tagId 経由で行う） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductTag() { }

    public ProductTag(UUID productId, UUID tagId) {
        this.productId = productId;
        this.tagId = tagId;
    }

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public UUID getProductId() { return productId; }
    public UUID getTagId() { return tagId; }
    public Instant getCreatedAt() { return createdAt; }
    public Product getProduct() { return product; }
    public Tag getTag() { return tag; }

    /** 複合主キー（別ファイルを増やさないため static class で定義） */
    public static class PK implements Serializable {
        public UUID productId;
        public UUID tagId;

        public PK() { }
        public PK(UUID productId, UUID tagId) { this.productId = productId; this.tagId = tagId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK other)) return false;
            return Objects.equals(productId, other.productId) && Objects.equals(tagId, other.tagId);
        }
        @Override public int hashCode() { return Objects.hash(productId, tagId); }
    }
}
