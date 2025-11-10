package com.hamas.reviewtrust.domain.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 変更ログ（監査）: 承認/非承認、表示切替、再取得などの操作履歴。
 * DB想定: audit_logs(id, actor_id, action, target_type, target_id, meta jsonb, created_at)
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** 追加情報(JSON)。Hibernate-types等は未前提のため raw JSON 文字列として保持。 */
    @Column(name = "meta", columnDefinition = "jsonb")
    private String metaJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(UUID id, UUID actorId, String action, String targetType, UUID targetId, String metaJson, Instant createdAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metaJson = metaJson;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public static AuditLog of(UUID actorId, String action, String targetType, UUID targetId, String metaJson) {
        return new AuditLog(null, actorId, action, targetType, targetId, metaJson, null);
    }

    // getters / setters
    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getMetaJson() { return metaJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public void setAction(String action) { this.action = action; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
}

