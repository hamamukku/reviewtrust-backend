package com.hamas.reviewtrust.api.public.v1;

import com.hamas.reviewtrust.api.public.v1.dto.ReviewDtos; // ← DTO側のpackageも public.v1.dto に統一すること
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 公開レビュー取得API
 * GET /api/products/{productId}/reviews?source=amazon|site&limit=50
 * 仕様: MVP公開APIに準拠。sourceの既定は "amazon"。limitは 1..50 にクリップ。
 */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public List<ReviewDtos.ReviewItem> list(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "amazon") String source,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Review.Source src = parseSource(source);
        int clipped = Math.max(1, Math.min(50, limit));
        return reviews.listPublished(productId, src, clipped).stream()
                .map(ReviewDtos::from)
                .toList();
    }

    private Review.Source parseSource(String s) {
        String k = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return "site".equals(k) ? Review.Source.SITE : Review.Source.AMAZON;
    }
}
