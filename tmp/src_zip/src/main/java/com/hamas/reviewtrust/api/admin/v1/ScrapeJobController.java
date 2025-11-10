// src/main/java/com/hamas/reviewtrust/api/admin/v1/ScrapeJobController.java
package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import com.hamas.reviewtrust.domain.scraping.service.ScrapingService.Result;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * スクレイプ・ジョブ操作（デバッグ/運用補助）
 * - POST /api/admin/scrape-jobs/asin/{asin}?limit=50
 * - POST /api/admin/scrape-jobs/url (url, limit, productId optional)
 *
 * レスポンスは AdminProductsController の形式と合わせる。
 */
@RestController
@RequestMapping("/api/admin/scrape-jobs")
@Validated
public class ScrapeJobController {

    private final ScrapingService scraping;

    public ScrapeJobController(ScrapingService scraping) {
        this.scraping = scraping;
    }

    /** ASIN 指定で収集を実行（同期） */
    @PostMapping("/asin/{asin}")
    public ResponseEntity<?> rescrapeByAsin(
            @PathVariable("asin") @NotBlank String asin,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit,
            @RequestParam(value = "locale", required = false) String localeTag // いまは未使用（将来拡張用）
    ) {
        try {
            // 現状のScrapingServiceは rescrape(asin, null, limit) でASINを解釈してURL化する
            Result r = scraping.rescrape(asin, null, limit);
            return ResponseEntity.ok(Map.of(
                    "productId",   asin,
                    "requestedUrl","asin://" + asin,
                    "asin",        asin,
                    "limit",       limit,
                    "collected",   r.getCount(),
                    "upserted",    r.getCount(),
                    "durationMs",  0L,
                    "message",     r.getMessage(),
                    "at",          Instant.now().toString()
            ));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Rescrape failed", e.getClass().getSimpleName());
        }
    }

    /** URL 指定で収集を実行（同期 / productId があれば UUID としてジョブ管理・なければASIN推定） */
    @PostMapping("/url")
    public ResponseEntity<?> rescrapeByUrl(
            @RequestParam("url") @NotBlank String url,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit,
            @RequestParam(value = "locale", required = false) String localeTag, // いまは未使用（将来拡張用）
            @RequestParam(value = "productId", required = false) String productId
    ) {
        try {
            // ScrapingService に rescrapeByUrl(String, Locale, int, String) があるケース（今回の改修版）
            Result r = scraping.rescrapeByUrl(url, null, limit, productId);
            String pidOrAsin = r.getAsin() != null ? r.getAsin() : (productId != null ? productId : "");
            return ResponseEntity.ok(Map.of(
                    "productId",   pidOrAsin,
                    "requestedUrl",url,
                    "asin",        r.getAsin(),
                    "limit",       limit,
                    "collected",   r.getCount(),
                    "upserted",    r.getCount(),
                    "durationMs",  0L,
                    "message",     r.getMessage(),
                    "at",          Instant.now().toString()
            ));
        } catch (NoSuchMethodError e) {
            // 旧版の ScrapingService には rescrapeByUrl が無い場合がある → 汎用rescrapeにフォールバック
            Result r = scraping.rescrape(productId, url, limit);
            String pidOrAsin = r.getAsin() != null ? r.getAsin() : (productId != null ? productId : "");
            return ResponseEntity.ok(Map.of(
                    "productId",   pidOrAsin,
                    "requestedUrl",url,
                    "asin",        r.getAsin(),
                    "limit",       limit,
                    "collected",   r.getCount(),
                    "upserted",    r.getCount(),
                    "durationMs",  0L,
                    "message",     r.getMessage(),
                    "at",          Instant.now().toString()
            ));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Rescrape failed", e.getClass().getSimpleName());
        }
    }

    private static ResponseEntity<Map<String,Object>> error(HttpStatus status, String code, String message, String details) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message, "details", details)
        ));
    }
}
