package com.hamas.reviewtrust.domain.products.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.audit.service.AuditService;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import com.hamas.reviewtrust.domain.scraping.parser.AmazonProductPageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ProductIntakeService.class);
    private static final String DEFAULT_URL_FORMAT = "https://www.amazon.co.jp/dp/%s";

    private final AmazonProductPageParser parser;
    private final ProductRepository products;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final Clock clock;

    public ProductIntakeService(AmazonProductPageParser parser,
                                ProductRepository products,
                                JdbcTemplate jdbc,
                                ObjectMapper objectMapper,
                                AuditService audit,
                                Clock clock) {
        this.parser = parser;
        this.products = products;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result registerOrUpdateFromHtml(String html, @Nullable String sourceUrl) {
        ProductPageSnapshot snapshot = parser.parse(html);
        String asin = snapshot.getAsin();
        if (!StringUtils.hasText(asin)) {
            throw new IllegalArgumentException("ASIN could not be extracted from HTML");
        }

        String url = resolveUrl(sourceUrl, asin);
        String title = snapshot.getTitle();
        if (!StringUtils.hasText(title)) {
            title = asin;
        }

        Product product = products.findByAsin(asin).orElse(null);
        boolean isNew = product == null;
        if (isNew) {
            Instant now = Instant.now(clock);
            product = new Product(UUID.randomUUID(), asin, title, title, url, true, now, now);
        }

        product.setAsin(asin);
        product.setTitle(title);
        product.setName(title);
        product.setUrl(url);
        product.setVisible(true);

        Product saved = products.save(product);
        persistSnapshot(saved.getId(), url, html, snapshot);
        recordAudit(saved, snapshot, url, isNew);

        log.info("[product-intake] {} productId={} asin={} partial={}",
                isNew ? "created" : "updated", saved.getId(), asin, snapshot.isPartial());
        return new Result(saved, snapshot, isNew);
    }

    private void persistSnapshot(UUID productId, String sourceUrl, String sourceHtml, ProductPageSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            jdbc.update("""
                    insert into product_snapshots
                        (product_id, source_url, snapshot_json, source_html, product_name, created_at)
                    values
                        (?, ?, cast(? as jsonb), ?, ?, ?)
                    """,
                    productId,
                    StringUtils.hasText(sourceUrl) ? sourceUrl : null,
                    json,
                    StringUtils.hasText(sourceHtml) ? sourceHtml : null,
                    StringUtils.hasText(snapshot.getTitle()) ? snapshot.getTitle().trim() : null,
                    Instant.now(clock)
            );
        } catch (JsonProcessingException e) {
            log.warn("[product-intake] failed to serialize snapshot for productId={}: {}", productId, e.getMessage());
        }
    }

    private void recordAudit(Product product, ProductPageSnapshot snapshot, String url, boolean isNew) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("asin", snapshot.getAsin());
        meta.put("productId", product.getId());
        meta.put("brand", nz(snapshot.getBrand()));
        meta.put("priceMinor", snapshot.getPriceMinor());
        meta.put("ratingAverage", snapshot.getRatingAverage());
        meta.put("ratingCount", snapshot.getRatingCount());
        meta.put("inlineReviewCount", snapshot.getInlineReviews().size());
        meta.put("partial", snapshot.isPartial());
        meta.put("sourceUrl", nz(url));
        meta.put("created", isNew);

        try {
            String metaJson = objectMapper.writeValueAsString(meta);
            audit.recordAction(actorUuid(), "PRODUCT_INTAKE_HTML", "PRODUCT", product.getId(), metaJson);
        } catch (JsonProcessingException e) {
            audit.recordAction(actorUuid(), "PRODUCT_INTAKE_HTML", "PRODUCT", product.getId(), "{}");
        }
    }

    private UUID actorUuid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = (authentication != null && StringUtils.hasText(authentication.getName()))
                ? authentication.getName()
                : "system";
        return UUID.nameUUIDFromBytes(principal.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveUrl(@Nullable String sourceUrl, String asin) {
        if (StringUtils.hasText(sourceUrl)) {
            return sourceUrl.trim();
        }
        return DEFAULT_URL_FORMAT.formatted(asin);
    }

    private String nz(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    public record Result(Product product, ProductPageSnapshot snapshot, boolean created) { }
}
