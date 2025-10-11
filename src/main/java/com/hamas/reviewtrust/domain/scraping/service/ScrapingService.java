// ScrapingService.java (placeholder)
package com.hamas.reviewtrust.domain.scraping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.audit.service.AuditService;
import com.hamas.reviewtrust.domain.scraping.client.BrowserClient;
import com.hamas.reviewtrust.domain.scraping.entity.ScrapeJob;
import com.hamas.reviewtrust.domain.scraping.exception.ScrapingExceptions;
import com.hamas.reviewtrust.domain.scraping.filter.ScrapeFilters;
import com.hamas.reviewtrust.domain.scraping.repo.ScrapeJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * スクレイピング実行サービス（MVP骨格）
 * - run(job, url): BrowserClientでHTML取得 → 解析（将来: AmazonReviewParser）→ upsert（将来）
 * - 成否は ScrapeJob の state/件数を更新、例外は AuditService へ記録
 * - filters(jsonb) は ScrapeFilters へデシリアライズ（MVPでは未使用でも保持）
 */
@Service
public class ScrapingService {

    private static final Logger log = LoggerFactory.getLogger(ScrapingService.class);

    private final BrowserClient browser;
    private final ScrapeJobRepository jobRepo;
    private final AuditService audit;
    private final ObjectMapper om;

    public ScrapingService(BrowserClient browser,
                           ScrapeJobRepository jobRepo,
                           AuditService audit,
                           ObjectMapper om) {
        this.browser = browser;
        this.jobRepo = jobRepo;
        this.audit = audit;
        this.om = om;
    }

    /**
     * 1ジョブ実行。呼び出し側で URL を与える（ProductRepository 未依存化のため）。
     * 成功: state=OK, targetCount/fetchedCount を反映（MVPでは0でプレース）
     * 失敗: state=FAILED, 例外ログへ（scope=scrape）
     */
    @Transactional
    public ScrapeJob run(ScrapeJob job, String targetUrl) {
        // filters の読込（MVPでは未使用でも受け取ってログへ）
        ScrapeFilters filters = null;
        try {
            if (job.getFiltersJson() != null) {
                filters = om.readValue(job.getFiltersJson(), ScrapeFilters.class);
            }
        } catch (Exception ignore) { /* 不正JSONはそのまま無視 */ }

        job.markRunning();
        jobRepo.save(job);

        try {
            String html = browser.getHtml(targetUrl, 750);
            if (html == null || html.isBlank()) {
                throw ScrapingExceptions.failed("empty response from browser", null);
            }

            // TODO: AmazonSelectors + AmazonReviewParser で解析 → 差分アップサート
            int targetCount = 0;   // 解析対象総数（将来）
            int fetchedCount = 0;  // 実際に保存した件数（将来）

            job.markOk(targetCount, fetchedCount);
            jobRepo.save(job);

            // 監査: SCRAPE_OK
            Map<String, Object> meta = new HashMap<>();
            meta.put("jobId", job.getId());
            meta.put("url", targetUrl);
            meta.put("filters", filters);
            meta.put("targetCount", targetCount);
            meta.put("fetchedCount", fetchedCount);
            audit.recordAction(systemActor(), "SCRAPE_OK", "PRODUCT", job.getProductId(), writeJson(meta));

            log.info("scrape OK: jobId={} productId={} fetched={}", job.getId(), job.getProductId(), fetchedCount);
            return job;

        } catch (ScrapingExceptions.ScrapeException se) {
            // 監査: SCRAPE_FAILED（コード付き）
            Map<String, Object> meta = new HashMap<>();
            meta.put("jobId", job.getId());
            meta.put("url", targetUrl);
            meta.put("code", se.getCode());
            meta.put("message", se.getMessage());

            job.markFailed();
            jobRepo.save(job);

            audit.recordException("scrape", se.getCode(), se, job.getId());
            audit.recordAction(systemActor(), "SCRAPE_FAILED", "PRODUCT", job.getProductId(), writeJson(meta));
            throw se;

        } catch (Exception ex) {
            job.markFailed();
            jobRepo.save(job);

            audit.recordException("scrape", "E_SCRAPE_FAILED", ex, job.getId());
            audit.recordAction(systemActor(), "SCRAPE_FAILED", "PRODUCT", job.getProductId(),
                    writeJson(Map.of("jobId", job.getId(), "url", targetUrl, "code", "E_SCRAPE_FAILED")));

            log.warn("scrape failed: jobId={} productId={}", job.getId(), job.getProductId(), ex);
            return job;
        }
    }

    private UUID systemActor() {
        return UUID.nameUUIDFromBytes("system/scraper".getBytes(StandardCharsets.UTF_8));
    }

    private String writeJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }
}
