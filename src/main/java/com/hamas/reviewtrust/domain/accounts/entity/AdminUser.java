
// src/main/java/com/hamas/reviewtrust/domain/accounts/entity/AdminUser.java
package com.hamas.reviewtrust.domain.accounts.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理者ユーザ（アカウント）
 * - username はユニーク
 * - passwordHash は AuthService 側で PBKDF2 などのハッシュを保存（平文禁止）
 */
@Entity
@Table(name = "admin_users",
       uniqueConstraints = @UniqueConstraint(name = "uk_admin_users_username", columnNames = "username"))
public class AdminUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "roles", length = 255)
    private String roles;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminUser() {
        // for JPA
    }

    public static AdminUser newActive(String username, String passwordHash) {
        AdminUser u = new AdminUser();
        u.id = UUID.randomUUID();
        u.username = username;
        u.passwordHash = passwordHash;
        u.roles = "ROLE_ADMIN";
        u.enabled = true;
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        return u;
    }

    @PrePersist
    public void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // getters / setters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public String getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEmail(String email) { this.email = email; }
    public void setRoles(String roles) { this.roles = roles; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminUser that)) return false;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() {
        return "AdminUser{id=%s, username=%s, enabled=%s}".formatted(id, username, enabled);
    }
}
