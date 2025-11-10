// src/main/java/com/hamas/reviewtrust/api/admin/v1/ApiProductServiceAdapter.java
package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiProductServiceAdapter implements AdminProductsController.ProductService {

    private final ProductRepository repo;
    @PersistenceContext
    private EntityManager em;

    public ApiProductServiceAdapter(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public AdminProductsController.Product createOrGetByUrl(String url, String name) {
        String asin = extractAsin(url).orElse(null);
        var pOpt = findByUrlOrAsin(url, asin);

        Product p = pOpt.orElseGet(() -> {
            String resolvedAsin = asin != null ? asin : UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            String displayName = (name != null && !name.isBlank()) ? name : resolvedAsin;
            String title = displayName;
            Product np = new Product(
                    null,
                    resolvedAsin,
                    displayName,
                    title,
                    url,
                    true,
                    Product.PublishStatus.PUBLISHED,
                    null,
                    null,
                    Instant.now(),
                    Instant.now()
            );
            return repo.save(np);
        });

        return new AdminProductsController.Product(
                p.getId().toString(),
                p.getTitle(),
                p.getUrl(),
                p.getCreatedAt()
        );
    }

    @Override
    public boolean exists(String productId) {
        try {
            return repo.existsById(UUID.fromString(productId));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AdminProductsController.ToggleVisibilityResult toggleVisibility(String productId, boolean visible) {
        UUID id;
        try {
            id = UUID.fromString(productId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        Product entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (visible) {
            entity.show();
        } else {
            entity.hide();
        }

        Product saved = repo.save(entity);
        return new AdminProductsController.ToggleVisibilityResult(saved.getId().toString(), saved.isVisible());
    }

    private Optional<Product> findByUrlOrAsin(String url, String asin) {
        var q = em.createQuery(
                "select p from Product p where p.url = :url or (:asin is not null and p.asin = :asin)",
                Product.class
        );
        q.setParameter("url", url);
        q.setParameter("asin", asin);
        return q.getResultStream().findFirst();
    }

    private static Optional<String> extractAsin(String url) {
        if (url == null) return Optional.empty();
        Pattern[] ps = new Pattern[] {
            Pattern.compile("/dp/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("/product-reviews/([A-Z0-9]{10})(?:/|\\?|$)"),
            Pattern.compile("[?&]asin=([A-Z0-9]{10})(?:&|$)")
        };
        for (Pattern p : ps) {
            Matcher m = p.matcher(url);
            if (m.find()) return Optional.ofNullable(m.group(1));
        }
        return Optional.empty();
    }
}
