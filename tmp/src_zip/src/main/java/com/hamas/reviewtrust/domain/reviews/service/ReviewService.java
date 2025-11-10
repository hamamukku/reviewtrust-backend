// ReviewService.java (placeholder)
package com.hamas.reviewtrust.domain.reviews.service;

import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * レビューの投稿（SITE: draft）と公開一覧取得の最小サービス。
 * - submitSiteDraft: 購入証明必須（proofImagePath）。stars 1..5 / text ≤1000
 * - listPublished: productId + source + limit(≤50) で新しい順
 * 仕様：投稿フロー/公開API要件に準拠。 
 */
@Service
public class ReviewService {

    private final ReviewRepository repo;
    private final ProductRepository products;

    public ReviewService(ReviewRepository repo, ProductRepository products) {
        this.repo = repo;
        this.products = products;
    }

    /** ユーザー投稿（SITE）を draft 保存。 */
    @Transactional
    public Review submitSiteDraft(
            UUID productId,
            int stars,
            String text,
            String proofImagePath,
            String reviewerName,
            Boolean reviewerPublic,
            String userRef
    ) {
        ensureProduct(productId);

        if (stars < 1 || stars > 5) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "stars must be 1..5");
        }
        if (!StringUtils.hasText(text) || text.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "text is required (≤1000 chars)");
        }
        if (!StringUtils.hasText(proofImagePath)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "proof image is required");
        }

        Review draft = Review.siteDraft(productId, stars, text.trim(), proofImagePath.trim(),
                nz(reviewerName), reviewerPublic, nz(userRef));
        return repo.save(draft);
    }

    /** 公開レビューの取得（新しい順）。limitは1..50にクリップ。 */
    @Transactional(readOnly = true)
    public List<Review> listPublished(UUID productId, Review.Source source, int limit) {
        ensureProduct(productId);
        int n = Math.max(1, Math.min(50, limit));
        return repo.findByProductIdAndSourceAndStatus(
                        productId, source, Review.Status.PUBLISHED,
                        PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    // --- helpers ---
    private void ensureProduct(UUID id) {
        products.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    private String nz(String s) { return StringUtils.hasText(s) ? s.trim() : null; }
}

