package com.hamas.reviewtrust.domain.reviews.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "review_scores")
public class ReviewScore {

    @EmbeddedId
    private Id id;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false, length = 1)
    private String rank;

    @Column(name = "sakura_judge", nullable = false, length = 16)
    private String sakuraJudge;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flags", columnDefinition = "jsonb", nullable = false)
    private JsonNode flags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb", nullable = false)
    private JsonNode rules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb", nullable = false)
    private JsonNode metrics;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReviewScore() {
    }

    public ReviewScore(Id id, double score, String rank, String sakuraJudge, JsonNode flags,
                       JsonNode rules, JsonNode metrics, Instant computedAt, Instant updatedAt) {
        this.id = id;
        this.score = score;
        this.rank = rank;
        this.sakuraJudge = sakuraJudge;
        this.flags = flags;
        this.rules = rules;
        this.metrics = metrics;
        this.computedAt = computedAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    public void onCreate() {
        if (computedAt == null) computedAt = Instant.now();
        if (updatedAt == null) updatedAt = computedAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Id getId() { return id; }
    public double getScore() { return score; }
    public String getRank() { return rank; }
    public String getSakuraJudge() { return sakuraJudge; }
    public JsonNode getFlags() { return flags; }
    public JsonNode getRules() { return rules; }
    public JsonNode getMetrics() { return metrics; }
    public Instant getComputedAt() { return computedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setScore(double score) { this.score = score; }
    public void setRank(String rank) { this.rank = rank; }
    public void setSakuraJudge(String sakuraJudge) { this.sakuraJudge = sakuraJudge; }
    public void setFlags(JsonNode flags) { this.flags = flags; }
    public void setRules(JsonNode rules) { this.rules = rules; }
    public void setMetrics(JsonNode metrics) { this.metrics = metrics; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(name = "source", nullable = false, length = 16)
        private String source;

        public Id() {
        }

        public Id(UUID productId, String source) {
            this.productId = productId;
            this.source = source;
        }

        public UUID getProductId() { return productId; }
        public String getSource() { return source; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id1)) return false;
            return Objects.equals(productId, id1.productId) && Objects.equals(source, id1.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, source);
        }
    }
}
