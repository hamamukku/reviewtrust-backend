// src/main/java/com/hamas/reviewtrust/api/admin/v1/AdminReviewsController.java
package com.hamas.reviewtrust.api.admin.v1;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * 管理系：レビューの承認/非承認（公開制御）
 * - POST /api/admin/reviews/{id}/approve
 * - POST /api/admin/reviews/{id}/reject    （理由 optional）
 *
 * 監査ログを必ず記録。状態遷移は MVP 仕様（ドラフト→承認→公開 / 非承認）に合わせる。
 */
@RestController
@RequestMapping("/api/admin/reviews")
@Validated
public class AdminReviewsController {

    private final ReviewService reviews;
    private final AuditLogService audit;

    public AdminReviewsController(ReviewService reviews, AuditLogService audit) {
        this.reviews = reviews;
        this.audit = audit;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") String reviewId) {
        try {
            Review r = reviews.approve(reviewId);
            audit.write("REVIEW_APPROVE", Map.of("reviewId", reviewId, "at", Instant.now().toString()));
            return ResponseEntity.ok(Map.of(
                    "id", r.id(),
                    "status", r.status(),
                    "visible", r.visible(),
                    "updatedAt", r.updatedAt().toString()
            ));
        } catch (ReviewNotFound e) {
            return error(HttpStatus.NOT_FOUND, "E_NOT_FOUND", e.getMessage(), null);
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, "E_STATE", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Approve failed", e.getClass().getSimpleName());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable("id") String reviewId,
                                    @RequestBody(required = false) RejectRequest body) {
        try {
            String reason = body != null ? body.reason() : null;
            Review r = reviews.reject(reviewId, reason);
            audit.write("REVIEW_REJECT", Map.of("reviewId", reviewId, "reason", reason, "at", Instant.now().toString()));
            return ResponseEntity.ok(Map.of(
                    "id", r.id(),
                    "status", r.status(),
                    "visible", r.visible(),
                    "updatedAt", r.updatedAt().toString()
            ));
        } catch (ReviewNotFound e) {
            return error(HttpStatus.NOT_FOUND, "E_NOT_FOUND", e.getMessage(), null);
        } catch (IllegalStateException e) {
            return error(HttpStatus.CONFLICT, "E_STATE", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Reject failed", e.getClass().getSimpleName());
        }
    }

    // ---- DTO / 期待IF ----
    public record RejectRequest(@NotBlank(message = "reason must not be blank") String reason) {}

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, String details) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message, "details", details)
        ));
    }

    public interface ReviewService {
        Review approve(String reviewId) throws ReviewNotFound, IllegalStateException;
        Review reject(String reviewId, String reason) throws ReviewNotFound, IllegalStateException;
    }
    public interface AuditLogService {
        void write(String action, Map<String, ?> payload);
    }

    // ---- Domain Snapshot ----
    public static class ReviewNotFound extends RuntimeException {
        public ReviewNotFound(String id) { super("Review not found: " + id); }
    }
    public record Review(String id, String status, boolean visible, Instant updatedAt) {}
}
