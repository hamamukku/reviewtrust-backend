// src/main/java/com/hamas/reviewtrust/api/admin/v1/ReviewModerationController.java
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.service.ModerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 管理：レビュー承認/非承認（レガシー互換）。
 *
 * 注意：
 * - 本実装は {@code controller.reviewModeration.enabled=true} のときのみ有効化されます
 *   （既定は無効 = AdminReviewsController と競合しない）
 * - 有効化する場合はエンドポイントの重複に注意してください。
 */
@RestController
@ConditionalOnProperty(
        value = "controller.reviewModeration.enabled",
        havingValue = "true",
        matchIfMissing = false // 既定で無効（衝突回避）
)
@RequestMapping("/api/admin/reviews")
public class ReviewModerationController {

    private final ModerationService moderation;

    public ReviewModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ModerationResult> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Reason body
    ) {
        Review r = moderation.approve(id, (body == null) ? null : body.reason());
        return ResponseEntity.ok(ModerationResult.of(r, body));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ModerationResult> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Reason body
    ) {
        Review r = moderation.reject(id, (body == null) ? null : body.reason());
        return ResponseEntity.ok(ModerationResult.of(r, body));
    }

    /** 入力：任意の理由 */
    public static final class Reason {
        private final String reason;

        @JsonCreator
        public Reason(@JsonProperty("reason") String reason) { this.reason = reason; }
        public String reason() { return reason; }
    }

    /** 出力：最小の結果構造 */
    public record ModerationResult(
            UUID reviewId,
            UUID productId,
            String newStatus, // "APPROVED" | "REJECTED"
            String reason
    ) {
        static ModerationResult of(Review r, Reason body) {
            return new ModerationResult(
                    r.getId(),
                    r.getProductId(),
                    r.getStatus().name(),
                    (body == null) ? null : body.reason()
            );
        }
    }
}
