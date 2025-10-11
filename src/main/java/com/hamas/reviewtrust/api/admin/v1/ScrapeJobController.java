// ScrapeJobController.java (placeholder)
package com.hamas.reviewtrust.api.admin.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.audit.service.AuditService;
import com.hamas.reviewtrust.domain.scraping.entity.ScrapeJob;
import com.hamas.reviewtrust.domain.scraping.filter.ScrapeFilters;
import com.hamas.reviewtrust.domain.scraping.repo.ScrapeJobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 手動再取得（キュー投入）API。
 * POST /api/admin/products/{id}/rescrape
 * body: ScrapeFilters（任意。nullなら全量方針/デフォルト）
 *
 * 返却: 202 Accepted {status:"queued", job_id, product_id, filters}
 * 監査: action=RESCRAPE_REQUEST, target_type=PRODUCT
 *
 * 仕様参照: MVP仕様 5.2「管理API」および バックエンド構成の scraping/* 群。 
 */
@RestController
@RequestMapping("/api/admin/products")
public class ScrapeJobController {

    private final ScrapeJobRepository jobRepo;
    private final AuditService audit;
    private final ObjectMapper om;

    public ScrapeJobController(ScrapeJobRepository jobRepo, AuditService audit, ObjectMapper om) {
        this.jobRepo = jobRepo;
        this.audit = audit;
        this.om = om;
    }

    @PostMapping("/{id}/rescrape")
    public ResponseEntity<?> enqueue(
            @PathVariable("id") UUID productId,
            @RequestBody(required = false) ScrapeFilters filters
    ) throws JsonProcessingException {

        if (filters != null) {
            filters.validate();
        }

        String filtersJson = (filters == null || filters.isEmpty()) ? null : om.writeValueAsString(filters);

        ScrapeJob job = jobRepo.save(ScrapeJob.queued(productId, filtersJson));

        // 監査メタ（filters とトリガー種別）をJSONに整形
        Map<String, Object> meta = new HashMap<>();
        meta.put("trigger", "manual");
        if (filters != null && !filters.isEmpty()) meta.put("filters", filters);

        // actorId はメール文字列から安定UUID化（MVP：Admin1名。後続で USERS 連携）
        UUID actorId = actorUuid();
        audit.recordAction(actorId, "RESCRAPE_REQUEST", "PRODUCT", productId, om.writeValueAsString(meta));

        Map<String, Object> body = new HashMap<>();
        body.put("status", "queued");
        body.put("job_id", job.getId());
        body.put("product_id", productId);
        if (filters != null && !filters.isEmpty()) body.put("filters", filters);
        body.put("enqueued_at", Instant.now());

        return ResponseEntity.accepted().body(body);
    }

    private UUID actorUuid() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String name = (a != null && StringUtils.hasText(a.getName())) ? a.getName() : "admin";
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
