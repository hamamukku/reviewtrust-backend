// src/main/java/com/hamas/reviewtrust/api/admin/v1/ApiReviewServiceAdapter.java
package com.hamas.reviewtrust.api.admin.v1;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * AdminReviewsController の ReviewService を JDBC で実装。
 * - JPA 依存なし（パッケージ不一致を回避）
 * - reviews テーブルに対して、承認(PUBLISHED)/却下(REJECTED) を直接 UPDATE
 */
@Component
@Transactional
public class ApiReviewServiceAdapter implements AdminReviewsController.ReviewService {

    private final JdbcTemplate jdbc;

    public ApiReviewServiceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminReviewsController.Review approve(String reviewId)
            throws AdminReviewsController.ReviewNotFound {
        UUID id = parse(reviewId);

        String actor = currentActor();
        // 可視/状態/監査系を一括更新（存在しないIDは 0 行→例外へ）
        String sql = """
            UPDATE public.reviews
               SET status = 'APPROVED',
                   visible = TRUE,
                   moderated_by = ?,
                   moderated_reason = NULL,
                   moderated_at = now(),
                   updated_at = now()
             WHERE id = ?
         RETURNING id, status, visible, updated_at
            """;
        try {
            return jdbc.queryForObject(sql, reviewRowMapper(), actor, id);
        } catch (EmptyResultDataAccessException e) {
            throw new AdminReviewsController.ReviewNotFound(reviewId);
        }
    }

    @Override
    public AdminReviewsController.Review reject(String reviewId, String reason)
            throws AdminReviewsController.ReviewNotFound, IllegalStateException {
        UUID id = parse(reviewId);

        String actor = currentActor();
        String r = (reason == null || reason.isBlank()) ? "rejected" : reason.trim();

        String sql = """
            UPDATE public.reviews
               SET status = 'REJECTED',
                   visible = FALSE,
                   moderated_by = ?,
                   moderated_reason = ?,
                   moderated_at = now(),
                   updated_at = now()
             WHERE id = ?
         RETURNING id, status, visible, updated_at
            """;
        try {
            return jdbc.queryForObject(sql, reviewRowMapper(), actor, r, id);
        } catch (EmptyResultDataAccessException e) {
            throw new AdminReviewsController.ReviewNotFound(reviewId);
        }
    }

    // ─────────── helpers ───────────

    private static UUID parse(String id) throws AdminReviewsController.ReviewNotFound {
        try { return UUID.fromString(id); }
        catch (Exception e) { throw new AdminReviewsController.ReviewNotFound(id); }
    }

    private static String currentActor() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElse("system");
    }

    private static RowMapper<AdminReviewsController.Review> reviewRowMapper() {
        return new RowMapper<>() {
            @Override
            public AdminReviewsController.Review mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
                String id = rs.getObject("id").toString();
                String status = rs.getString("status");
                boolean visible = rs.getBoolean("visible");
                Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
                return new AdminReviewsController.Review(id, status, visible, updatedAt);
            }
        };
    }
}
