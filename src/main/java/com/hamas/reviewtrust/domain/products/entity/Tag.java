// Tag.java (placeholder)
package com.hamas.reviewtrust.domain.products.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * カテゴリタグ（1段）。name は一意。
 * 将来: ProductTag で多対多を表現（本ファイルでは関連を張らない最小形）。 
 */
@Entity
@Table(name = "tags",
       uniqueConstraints = @UniqueConstraint(name = "uk_tags_name", columnNames = "name"),
       indexes = @Index(name = "ix_tags_name", columnList = "name"))
public class Tag {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tag() { }

    public Tag(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    // getters / setters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setName(String name) { this.name = name; }
}
