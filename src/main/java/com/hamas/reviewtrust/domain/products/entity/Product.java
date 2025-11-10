// src/main/java/com/hamas/reviewtrust/domain/products/entity/Product.java
package com.hamas.reviewtrust.domain.products.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * PRODUCTS: id, asin(UNIQUE), name, title, url, visible, publish_status, published_at, hidden_at,
 *           created_at, updated_at
 * 仕様: MVPスキーマ準拠（name/title は初回登録時にASINベースの仮称を投入）。
 * - 410動作: Controller 側で visible=false / publish_status≠PUBLISHED を検知して Gone を返す想定。
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "ix_products_asin", columnList = "asin"),
        @Index(name = "ix_products_visible_status", columnList = "visible,publish_status")
    }
)
public class Product {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String asin;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean visible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 16)
    private PublishStatus publishStatus = PublishStatus.PUBLISHED;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // for JPA
    }

    // ===== 互換コンストラクタ（旧テスト・旧サービス呼び出し維持） =====
    // 例: Product(UUID, String, String, String, boolean, Instant, Instant)
    public Product(UUID id, String asin, String title, String url,
                   boolean visible, Instant createdAt, Instant updatedAt) {
        this(id, asin, title, title, url, visible, PublishStatus.PUBLISHED, null, null, createdAt, updatedAt);
    }

    public Product(UUID id, String asin, String name, String title, String url,
                   boolean visible, Instant createdAt, Instant updatedAt) {
        this(id, asin, name, title, url, visible, PublishStatus.PUBLISHED, null, null, createdAt, updatedAt);
    }

    // ===== 新コンストラクタ（完全版） =====
    public Product(UUID id,
                   String asin,
                   String name,
                   String title,
                   String url,
                   boolean visible,
                   PublishStatus publishStatus,
                   Instant publishedAt,
                   Instant hiddenAt,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = id;
        this.asin = asin;
        this.name = name;
        this.title = title;
        this.url = url;
        this.visible = visible;
        this.publishStatus = (publishStatus != null) ? publishStatus : PublishStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.hiddenAt = hiddenAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- lifecycle ---
    @PrePersist
    public void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.name == null || this.name.isBlank()) {
            this.name = this.asin;
        }
        if (this.title == null || this.title.isBlank()) {
            this.title = this.asin;
        }
        if (this.publishStatus == null) {
            this.publishStatus = PublishStatus.PUBLISHED;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- domain helpers（任意で使用） ---
    public void publish() {
        this.publishStatus = PublishStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.visible = true;
        this.hiddenAt = null;
    }

    public void approve() {
        this.publishStatus = PublishStatus.APPROVED;
    }

    public void draft() {
        this.publishStatus = PublishStatus.DRAFT;
    }

    public void archive() {
        this.publishStatus = PublishStatus.ARCHIVED;
        this.visible = false;
        this.hiddenAt = Instant.now();
    }

    public void hide() {
        this.visible = false;
        this.hiddenAt = Instant.now();
    }

    public void show() {
        this.visible = true;
        this.hiddenAt = null;
    }

    // --- getters / setters ---
    public UUID getId() {
        return id;
    }

    public String getAsin() {
        return asin;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public boolean isVisible() {
        return visible;
    }

    public PublishStatus getPublishStatus() {
        return publishStatus;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setAsin(String asin) {
        this.asin = asin;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setPublishStatus(PublishStatus status) {
        this.publishStatus = status;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setHiddenAt(Instant hiddenAt) {
        this.hiddenAt = hiddenAt;
    }

    public enum PublishStatus { DRAFT, APPROVED, PUBLISHED, ARCHIVED }
}

