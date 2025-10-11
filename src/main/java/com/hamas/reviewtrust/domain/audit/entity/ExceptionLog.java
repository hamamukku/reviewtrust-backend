// ExceptionLog.java (placeholder)
package com.hamas.reviewtrust.domain.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 例外ログ（一次原因の特定用）。MVP仕様の EXCEPTION_LOGS に準拠。 
 * カラム: id, job_id(NULL可), scope(scrape|scoring|api), error_code, message, stack, created_at
 */
@Entity
@Table(name = "exception_logs")
public class ExceptionLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId; // NULL可

    @Column(nullable = false)
    private String scope; // "scrape" | "scoring" | "api" 等

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(columnDefinition = "text", nullable = false)
    private String message;

    @Column(columnDefinition = "text", nullable = false)
    private String stack;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExceptionLog() { }

    public ExceptionLog(UUID id, UUID jobId, String scope, String errorCode, String message, String stack, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.scope = scope;
        this.errorCode = errorCode;
        this.message = message;
        this.stack = stack;
        this.createdAt = createdAt;
    }

    @PrePersist
    public void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public static ExceptionLog of(String scope, String errorCode, String message, String stack, UUID jobId) {
        return new ExceptionLog(null, jobId, scope, errorCode, message, stack, null);
    }

    // getters / setters
    public UUID getId() { return id; }
    public UUID getJobId() { return jobId; }
    public String getScope() { return scope; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getStack() { return stack; }
    public Instant getCreatedAt() { return createdAt; }

    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public void setScope(String scope) { this.scope = scope; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setMessage(String message) { this.message = message; }
    public void setStack(String stack) { this.stack = stack; }
}
