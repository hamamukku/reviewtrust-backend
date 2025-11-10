package com.hamas.reviewtrust.domain.products.dto;

import com.hamas.reviewtrust.domain.products.entity.Product;

import java.time.Instant;

/**
 * Lightweight projection for product lists exposed via the public API.
 */
public record ProductListItem(
        String id,
        String asin,
        String title,
        String name,
        String url,
        boolean visible,
        String publishStatus,
        double score,
        String rank,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant hiddenAt
) {

    public static ProductListItem from(Product product, double score, String rank) {
        return new ProductListItem(
                product.getId() != null ? product.getId().toString() : null,
                product.getAsin(),
                product.getTitle(),
                product.getName(),
                product.getUrl(),
                product.isVisible(),
                product.getPublishStatus() != null ? product.getPublishStatus().name() : null,
                score,
                rank,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getPublishedAt(),
                product.getHiddenAt()
        );
    }
}
