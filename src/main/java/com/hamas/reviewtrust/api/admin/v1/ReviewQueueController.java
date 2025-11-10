package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/review-queue")
public class ReviewQueueController {

    private static final Logger log = LoggerFactory.getLogger(ReviewQueueController.class);

    private final ReviewService reviewService;

    public ReviewQueueController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "type", required = false, defaultValue = "USER") String typeParam,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer pageParam,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSizeParam,
            @RequestParam(value = "flag", required = false) String flagParam
    ) {
        int requestedPage = Optional.ofNullable(pageParam).orElse(1);
        int requestedSize = Optional.ofNullable(pageSizeParam).orElse(20);
        int safePage = Math.max(1, requestedPage);
        int zeroBasedPage = safePage - 1;
        int safeSize = clampPageSize(requestedSize);

        try {
            Review.Source source = resolveSource(typeParam);
            if (StringUtils.hasText(flagParam)) {
                log.debug("[review-queue] flag parameter '{}' is currently ignored", flagParam);
            }

            Page<Review> page = reviewService.listQueue(source, zeroBasedPage, safeSize);
            List<QueueItem> items = page.getContent().stream().map(QueueItem::from).toList();
            return ResponseEntity.ok(new QueueResponse(items, page.getTotalElements(), safePage, safeSize));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "E_BAD_REQUEST", "message", e.getMessage())));
        } catch (Exception e) {
            log.warn("[review-queue] failed to load queue type={} page={} size={}", typeParam, safePage, safeSize, e);
            return ResponseEntity.ok(new QueueResponse(List.of(), 0L, safePage, safeSize));
        }
    }

    private Review.Source resolveSource(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Review.Source.SITE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "USER" -> Review.Source.USER;
            case "SITE" -> Review.Source.SITE;
            case "AMAZON" -> Review.Source.AMAZON;
            case "ALL", "*" -> null;
            default -> throw new IllegalArgumentException("type must be USER, SITE or AMAZON");
        };
    }

    private int clampPageSize(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, 200);
    }

    public record QueueResponse(List<QueueItem> items, long total, int page, int pageSize) { }

    public record QueueItem(
            String id,
            String productId,
            String source,
            int stars,
            String text,
            boolean hasProof,
            List<String> images,
            String createdAt,
            String proofUrl
    ) {
        static QueueItem from(Review review) {
            String productId = Optional.ofNullable(review.getProductId()).map(UUID::toString).orElse(null);
            String proof = review.getProofUrl();
            List<String> images = StringUtils.hasText(proof) ? List.of(proof) : List.of();
            Instant createdAt = Optional.ofNullable(review.getCreatedAt()).orElseGet(Instant::now);
            return new QueueItem(
                    Optional.ofNullable(review.getId()).map(UUID::toString).orElse(null),
                    productId,
                    Optional.ofNullable(review.getSource()).map(Enum::name).map(String::toLowerCase).orElse("site"),
                    review.getStars(),
                    review.getText(),
                    StringUtils.hasText(proof),
                    images,
                    createdAt.toString(),
                    proof
            );
        }
    }
}
