// Product.java (placeholder)
package com.hamas.reviewtrust.domain.products.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * PRODUCTS: id, asin(UNIQUE), title, url, visible, created_at, updated_at
 * 仕様: MVPスキーマに準拠。titleは登録時に未取得でも一旦ASINを入れておく。 
 */
@Entity
@Table(name = "products",
       indexes = {
           @Index(name = "ix_products_asin", columnList = "asin"),
           @Index(name = "ix_products_visible", columnList = "visible")
       })
public class Product {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String asin;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { }

    public Product(UUID id, String asin, String title, String url, boolean visible,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.asin = asin;
        this.title = title;
        this.url = url;
        this.visible = visible;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.title == null || this.title.isBlank()) this.title = this.asin;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // getters / setters
    public UUID getId() { return id; }
    public String getAsin() { return asin; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public boolean isVisible() { return visible; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAsin(String asin) { this.asin = asin; }
    public void setTitle(String title) { this.title = title; }
    public void setUrl(String url) { this.url = url; }
    public void setVisible(boolean visible) { this.visible = visible; }
}
