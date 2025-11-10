package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.audit.service.AuditService;
import com.hamas.reviewtrust.domain.reviews.entity.Review;
import com.hamas.reviewtrust.domain.reviews.repo.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 管理向けの承認/非承認サービス。
 * - approve(id, reason)  : status=PENDING/DRAFT → APPROVED
 * - reject(id,  reason)  : status=PENDING/DRAFT → REJECTED
 * 監査ログに action=REVIEW_APPROVED / REVIEW_REJECTED を記録（MVPの監査要件）。 
 */
@Service
public class ModerationService {

    private final ReviewRepository repo;
    private final AuditService audit;
    private final ObjectMapper om;

    public ModerationService(ReviewRepository repo, AuditService audit, ObjectMapper om) {
        this.repo = repo;
        this.audit = audit;
        this.om = om;
    }

    @Transactional
    public Review approve(UUID reviewId, String reason) {
        Review r = loadPending(reviewId);
        r.setStatus(Review.Status.APPROVED);
        repo.save(r);

        recordAudit("REVIEW_APPROVED", r, reason);
        return r;
    }

    @Transactional
    public Review reject(UUID reviewId, String reason) {
        Review r = loadPending(reviewId);
        r.setStatus(Review.Status.REJECTED);
        repo.save(r);

        recordAudit("REVIEW_REJECTED", r, reason);
        return r;
    }

    // ---- helpers ----

    private Review loadPending(UUID id) {
        Review r = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (r.getStatus() != Review.Status.DRAFT && r.getStatus() != Review.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "review is not pending");
        }
        return r;
    }

    private void recordAudit(String action, Review r, String reason) {
        UUID actor = actorUuid();
        try {
            String meta = om.writeValueAsString(Map.of(
                    "productId", r.getProductId(),
                    "reviewId", r.getId(),
                    "reason", nz(reason),
                    "source", r.getSource().name()
            ));
            audit.recordAction(actor, action, "REVIEW", r.getId(), meta);
        } catch (Exception e) {
            audit.recordAction(actor, action, "REVIEW", r.getId(), "{}");
        }
    }

    private UUID actorUuid() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String name = (a != null && StringUtils.hasText(a.getName())) ? a.getName() : "admin";
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private String nz(String s) { return StringUtils.hasText(s) ? s : null; }
}

