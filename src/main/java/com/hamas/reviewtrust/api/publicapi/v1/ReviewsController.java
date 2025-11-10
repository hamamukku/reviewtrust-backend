package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ReviewDtos;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import com.hamas.reviewtrust.domain.reviews.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ReviewsController {

    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final ProductRepository productRepository;

    public ReviewsController(
            ReviewRepository reviewRepository,
            ReviewService reviewService,
            ProductRepository productRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.productRepository = productRepository;
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<ReviewDtos.ReviewItem>> list(
            @PathVariable("id") String rawProductId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID productId = resolveProductId(rawProductId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> page = reviewRepository.findPublicReviews(productId, pageable);
        return ResponseEntity.ok(ReviewDtos.fromAll(page.getContent()));
    }

    @GetMapping(value = "/{id}/reviews/grouped", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<ReviewDtos.ReviewItem>>> grouped(
            @PathVariable("id") String rawProductId
    ) {
        UUID productId = resolveProductId(rawProductId);
        List<Review> amazon = reviewRepository.findAmazonReviews(productId);
        List<Review> users = reviewRepository.findApprovedUserReviews(productId);
        Map<String, List<ReviewDtos.ReviewItem>> grouped = new LinkedHashMap<>();
        grouped.put("amazon", ReviewDtos.fromAll(amazon));
        grouped.put("user", ReviewDtos.fromAll(users));
        return ResponseEntity.ok(grouped);
    }

    @PostMapping(
            value = "/{id}/reviews",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> submit(
            @PathVariable("id") String rawProductId,
            @RequestParam("stars") double stars,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("body") String body,
            @RequestParam(value = "proof", required = false) MultipartFile proof
    ) {
        UUID productId = resolveProductId(rawProductId);
        reviewService.createUserReview(productId, stars, title, body, proof);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private UUID resolveProductId(String rawId) {
        if (!StringUtils.hasText(rawId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product id is required");
        }
        String trimmed = rawId.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ex) {
            return productRepository.findByAsin(trimmed)
                    .map(product -> product.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        }
    }
}
