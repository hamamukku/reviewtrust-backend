package com.hamas.reviewtrust.api.publicapi.v1;

import com.hamas.reviewtrust.domain.products.dto.ProductListItem;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.service.ProductService;
import com.hamas.reviewtrust.domain.scoring.catalog.ScoreModels;
import com.hamas.reviewtrust.domain.scoring.engine.ScoreService;
import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public facing products API. Provides read endpoints for product listings/details and a protected
 * registration endpoint that triggers scraping tasks for administrators.
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(ProductsController.class);
    private static final int DEFAULT_RESCRAPE_LIMIT = 50;

    private final ProductService productService;
    private final ScoreService scoreService;
    private final ScrapingService scrapingService;

    @PersistenceContext
    private EntityManager entityManager;

    public ProductsController(ProductService productService,
                              ScoreService scoreService,
                              ScrapingService scrapingService) {
        this.productService = productService;
        this.scoreService = scoreService;
        this.scrapingService = scrapingService;
    }

    @GetMapping
    public ResponseEntity<List<ProductListItem>> list(
            @RequestParam(value = "visible", required = false) String visibleParam,
            @RequestParam(value = "q", required = false) String queryParam,
            @RequestParam(value = "tag", required = false) String tagParam,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer pageParam,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSizeParam) {

        Boolean visible = parseNullableBoolean(visibleParam);
        String query = normalise(queryParam);
        String tag = normalise(tagParam);

        int safePage = Math.max(0, Optional.ofNullable(pageParam).orElse(0));
        int safePageSize = clampPageSize(Optional.ofNullable(pageSizeParam).orElse(50));

        List<ProductListItem> items = productService.findProductSummaries(query, tag, visible, safePage, safePageSize);
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ProductDetailResponse detail(@PathVariable("id") UUID id) {
        Product product = entityManager.find(Product.class, id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        Optional<ScoreModels.ScoreResult> maybeScore = scoreService.computeForProduct(id.toString());
        return ProductDetailResponse.from(product, maybeScore.orElse(null));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody ProductRegistrationRequest request,
            @RequestParam(value = "rescrape", defaultValue = "false") boolean rescrape,
            Authentication authentication) {

        String principal = authentication != null ? authentication.getName() : "anonymous";
        String rawInput = request.rawInput();
        log.info("POST /api/products requested by {} payload='{}' rescrape={}", principal, rawInput, rescrape);

        String normalizedInput = request.resolveInput();
        ProductService.RegistrationResult registrationResult = productService.register(normalizedInput);
        Product product = Objects.requireNonNull(registrationResult.product(), "product must not be null");

        boolean shouldEnqueue = rescrape || registrationResult.created();
        boolean enqueued = false;
        ScrapingService.Result scrapingResult = null;

        if (shouldEnqueue && product.getId() != null) {
            try {
                scrapingResult = scrapingService.rescrape(
                        product.getId().toString(),
                        product.getUrl(),
                        DEFAULT_RESCRAPE_LIMIT
                );
                enqueued = scrapingResult != null && scrapingResult.isSuccess();
            } catch (Exception ex) {
                log.warn("SCRAPE_ENQUEUE_FAILED productId={} cause={}", product.getId(), ex.toString(), ex);
            }
        }

        Map<String, Object> productMap = new LinkedHashMap<>();
        productMap.put("id", product.getId() != null ? product.getId().toString() : null);
        productMap.put("asin", product.getAsin());
        productMap.put("title", product.getTitle());
        productMap.put("name", product.getName());
        productMap.put("url", product.getUrl());
        productMap.put("visible", product.isVisible());
        productMap.put("publishStatus", product.getPublishStatus() != null ? product.getPublishStatus().name() : null);
        productMap.put("createdAt", product.getCreatedAt());
        productMap.put("updatedAt", product.getUpdatedAt());
        productMap.put("publishedAt", product.getPublishedAt());
        productMap.put("hiddenAt", product.getHiddenAt());

        Map<String, Object> scrapeMap = new LinkedHashMap<>();
        scrapeMap.put("enqueued", enqueued);
        if (scrapingResult != null) {
            scrapeMap.put("fallbackUsed", scrapingResult.isFallbackUsed());
            scrapeMap.put("summary", scrapingResult.getProductSnapshot());
        } else {
            scrapeMap.put("fallbackUsed", false);
            scrapeMap.put("summary", null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", productMap);
        body.put("created", registrationResult.created());
        body.put("updated", registrationResult.updated());
        body.put("scrape", scrapeMap);

        return ResponseEntity.ok(body);
    }

    private Boolean parseNullableBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "on" -> Boolean.TRUE;
            case "false", "0", "no", "off" -> Boolean.FALSE;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "visible must be one of true/false/1/0/on/off"
            );
        };
    }

    private String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int clampPageSize(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, 200);
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    // --- DTO definitions ----------------------------------------------------

    public record ProductRegistrationRequest(
            String input,
            String url,
            String asin
    ) {
        String rawInput() {
            if (input != null) return input;
            if (url != null) return url;
            if (asin != null) return asin;
            return null;
        }

        String resolveInput() {
            if (hasText(input)) {
                return input.trim();
            }
            if (hasText(url)) {
                return url.trim();
            }
            if (hasText(asin)) {
                return asin.trim();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input, url or asin is required");
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    public record ProductDetailResponse(
            String id,
            String asin,
            String title,
            String name,
            String url,
            boolean visible,
            String publishStatus,
            Integer score,
            String rank,
            String sakuraJudge,
            List<String> flags,
            Map<String, Object> metrics,
            Instant updatedAt
    ) {
        static ProductDetailResponse from(Product product, ScoreModels.ScoreResult score) {
            Instant updatedAt = product.getUpdatedAt();
            Integer scoreValue = null;
            String rank = null;
            String sakuraJudge = null;
            List<String> flags = List.of();
            Map<String, Object> metrics = Map.of();
            if (score != null) {
                scoreValue = score.score;
                rank = score.rank != null ? score.rank.name() : null;
                sakuraJudge = score.sakuraJudge != null ? score.sakuraJudge.name() : null;
                flags = score.flags != null ? score.flags : List.of();
                metrics = score.metrics != null ? score.metrics : Map.of();
                updatedAt = parseInstant(score.computedAt, updatedAt);
            }
            String resolvedTitle = StringUtils.hasText(product.getTitle()) ? product.getTitle() : product.getName();
            String resolvedName = StringUtils.hasText(product.getName()) ? product.getName() : resolvedTitle;
            return new ProductDetailResponse(
                    product.getId() != null ? product.getId().toString() : null,
                    product.getAsin(),
                    resolvedTitle,
                    resolvedName,
                    product.getUrl(),
                    product.isVisible(),
                    product.getPublishStatus() != null ? product.getPublishStatus().name() : null,
                    scoreValue,
                    rank,
                    sakuraJudge,
                    flags,
                    metrics,
                    updatedAt
            );
        }
    }
}
