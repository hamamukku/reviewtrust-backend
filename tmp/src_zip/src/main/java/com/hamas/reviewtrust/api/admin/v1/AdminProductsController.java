// src/main/java/com/hamas/reviewtrust/api/admin/v1/AdminProductsController.java
package com.hamas.reviewtrust.api.admin.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * 管理系：商品登録 & 再スクレイプ実行
 * - POST /api/admin/products
 * - POST /api/admin/products/{id}/rescrape?url=...&asin=...&limit=50
 *
 * エラーは JSON 統一 {"error":{"code","message","details"}} を返す。
 */
@RestController
@RequestMapping("/api/admin/products")
@Validated
public class AdminProductsController {

    private static final java.util.regex.Pattern AMAZON_URL = java.util.regex.Pattern.compile(
            "^https://www\\.amazon\\.co\\.jp/(?:dp|gp/product)/[A-Z0-9]{10}(?:/)?(?:\\?.*)?$"
    );
    private static final java.util.regex.Pattern ASIN_PATTERN = java.util.regex.Pattern.compile("^[A-Z0-9]{10}$");

    private final ProductService productService;
    private final ScrapeService scrapeService;
    private final AuditLogService audit;

    public AdminProductsController(ProductService productService,
                                   ScrapeService scrapeService,
                                   AuditLogService audit) {
        this.productService = productService;
        this.scrapeService = scrapeService;
        this.audit = audit;
    }

    /** 商品登録（存在すれば返す/なければ作る） */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateProductRequest in) {
        try {
            // Amazon URL 厳格バリデーション
            if (!AMAZON_URL.matcher(in.url()).matches()) {
                return error(HttpStatus.BAD_REQUEST, "E_VALIDATION",
                        "url must match https://www.amazon.co.jp/(dp|gp/product)/<ASIN>", "pattern_mismatch");
            }

            Product p = productService.createOrGetByUrl(in.url(), in.name());
            audit.write("PRODUCT_CREATE_OR_GET",
                    Map.of("productId", p.id(), "url", p.url(), "name", p.name(), "at", Instant.now().toString()));

            return ResponseEntity
                    .created(URI.create("/api/admin/products/" + p.id()))
                    .body(Map.of(
                            "id", p.id(),
                            "name", p.name(),
                            "url", p.url(),
                            "createdAt", p.createdAt().toString()
                    ));
        } catch (DuplicateKeyException e) {
            return error(HttpStatus.CONFLICT, "E_CONFLICT", "Product already exists", "duplicate_key");
        } catch (PatternSyntaxException e) { // ← IllegalArgumentException の前に配置
            return error(HttpStatus.BAD_REQUEST, "E_VALIDATION", "invalid url pattern", e.getClass().getSimpleName());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Failed to register product", e.getClass().getSimpleName());
        }
    }

    /** 再スクレイプ（同期） */
    @PostMapping("/{id}/rescrape")
    public ResponseEntity<?> rescrape(
            @PathVariable("id") String productId,
            @RequestParam(value = "url",   required = false) String url,
            @RequestParam(value = "asin",  required = false) String asin,
            @RequestParam(value = "limit", defaultValue = "50") @Min(1) int limit
    ) {
        try {
            if (!productService.exists(productId)) {
                return error(HttpStatus.NOT_FOUND, "E_NOT_FOUND", "Product not found: " + productId, null);
            }
            if ((url == null || url.isBlank()) && (asin == null || asin.isBlank())) {
                return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST",
                        "Either 'url' or 'asin' parameter is required", "missing_url_or_asin");
            }
            if (url != null && !url.isBlank() && !AMAZON_URL.matcher(url).matches()) {
                return error(HttpStatus.BAD_REQUEST, "E_VALIDATION",
                        "url must match https://www.amazon.co.jp/(dp|gp/product)/<ASIN>", "pattern_mismatch");
            }
            if (asin != null && !asin.isBlank() && !ASIN_PATTERN.matcher(asin).matches()) {
                return error(HttpStatus.BAD_REQUEST, "E_VALIDATION", "asin must be [A-Z0-9]{10}", "asin_pattern");
            }

            String target = (asin != null && !asin.isBlank()) ? asin : productId;
            ScrapeReport r = scrapeService.rescrape(target, url, limit);

            audit.write("PRODUCT_RESCRAPE",
                    Map.of("productId", productId,
                           "requestedUrl", url,
                           "asin", asin,
                           "limit", limit,
                           "collected", r.collected(),
                           "upserted", r.upserted(),
                           "durationMs", r.durationMs(),
                           "at", Instant.now().toString()));

            return ResponseEntity.ok(Map.of(
                    "productId",  productId,
                    "requestedUrl", url,
                    "asin", asin,
                    "limit", limit,
                    "collected", r.collected(),
                    "upserted", r.upserted(),
                    "durationMs", r.durationMs()
            ));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "E_BAD_REQUEST", e.getMessage(), null);
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "E_INTERNAL", "Rescrape failed", e.getClass().getSimpleName());
        }
    }

    // ---- DTO / ヘルパ ----

    public record CreateProductRequest(
            @NotBlank(message = "name is required") String name,
            @NotBlank(message = "url is required")
            @Pattern(
                regexp = "^https://www\\.amazon\\.co\\.jp/(?:dp|gp/product)/[A-Z0-9]{10}(?:/)?(?:\\?.*)?$",
                message = "url must be https://www.amazon.co.jp/(dp|gp/product)/<ASIN>"
            )
            String url
    ) {}

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, String details) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message, "details", details)
        ));
    }

    // ---- Service 期待インターフェース ----
    public interface ProductService {
        Product createOrGetByUrl(String url, String name);
        boolean exists(String productId);
    }
    public interface ScrapeService {
        ScrapeReport rescrape(String productIdOrAsin, String url, int limit);
    }
    public interface AuditLogService {
        void write(String action, Map<String, ?> payload);
    }

    // ---- Domain Snapshot ----
    public record Product(String id, String name, String url, Instant createdAt) {}
    public record ScrapeReport(int collected, int upserted, long durationMs) {}
}

