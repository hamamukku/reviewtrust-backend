package com.hamas.reviewtrust.domain.reviews.service;

import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import com.hamas.reviewtrust.domain.reviews.storage.ReviewProofStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
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
    private final ReviewProofStorage proofStorage;

    private static final List<Review.Status> PENDING_STATUSES = List.of(Review.Status.PENDING, Review.Status.DRAFT);
    private static final long MAX_PROOF_BYTES = 10L * 1024 * 1024;

    public ReviewService(ReviewRepository repo, ProductRepository products, ReviewProofStorage proofStorage) {
        this.repo = repo;
        this.products = products;
        this.proofStorage = proofStorage;
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

    /** 一般ユーザーの投稿を pending 保存。 */
    @Transactional
    public Review createUserReview(
            UUID productId,
            double stars,
            String title,
            String body,
            MultipartFile proof
    ) {
        ensureProduct(productId);
        int rounded = (int) Math.round(stars);
        if (rounded < 1 || rounded > 5) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "stars must be 1..5");
        }
        if (!StringUtils.hasText(body)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "body is required");
        }
        String safeBody = body.trim();
        if (safeBody.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "body must be <= 1000 characters");
        }
        String safeTitle = nz(title);
        if (safeTitle != null && safeTitle.length() > 200) {
            safeTitle = safeTitle.substring(0, 200);
        }

        boolean hasProof = proof != null && !proof.isEmpty();
        String proofPath = null;
        if (hasProof) {
            validateProof(proof);
            proofPath = storeProof(proof);
        }

        Review review = Review.userSubmission(productId, rounded, safeTitle, safeBody, hasProof, proofPath, Instant.now());
        return repo.save(review);
    }

    @Transactional
    public Review approveReview(UUID reviewId) {
        Review review = repo.findById(reviewId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (!PENDING_STATUSES.contains(review.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "review is not pending");
        }
        review.setStatus(Review.Status.APPROVED);
        review.setPostedAt(Instant.now());
        return repo.save(review);
    }

    /** 公開レビューの取得（新しい順）。limitは1..50にクリップ。 */
    @Transactional(readOnly = true)
    public List<Review> listPublished(UUID productId, Review.Source source, int limit) {
        ensureProduct(productId);
        int n = Math.max(1, Math.min(50, limit));
        Review.Status status = (source == Review.Source.AMAZON) ? Review.Status.PUBLISHED : Review.Status.APPROVED;
        return repo.findByProductIdAndSourceAndStatus(
                        productId, source, status,
                        PageRequest.of(0, n, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    /** ���F�L���[�ԑO�̓��e�ꗗ�iDRAFT�j�Bpage:0-based, size:1..200�ɃN���b�v�B */
    @Transactional(readOnly = true)
    public Page<Review> listQueue(Review.Source source, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (source != null) {
            return repo.findBySourceAndStatusIn(source, PENDING_STATUSES, pageable);
        }
        return repo.findByStatusIn(PENDING_STATUSES, pageable);
    }

    // --- helpers ---
    private void ensureProduct(UUID id) {
        products.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    private String nz(String s) { return StringUtils.hasText(s) ? s.trim() : null; }

    private void validateProof(MultipartFile file) {
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "proof file must be <=10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            return;
        }
        if (contentType.startsWith("image/")) {
            return;
        }
        if ("application/pdf".equalsIgnoreCase(contentType)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "proof must be image or pdf");
    }

    private String storeProof(MultipartFile file) {
        try {
            return proofStorage.saveProof(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store proof");
        }
    }
}

