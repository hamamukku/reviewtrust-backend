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
            Product np = new Product(
                    null,
                    asin != null ? asin : UUID.randomUUID().toString().substring(0,10).toUpperCase(),
                    (name != null && !name.isBlank()) ? name : (asin != null ? asin : "Unknown"),
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
