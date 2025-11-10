package com.hamas.reviewtrust.domain.products.mapper;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductDtos.ProductDetail;
import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductDtos.ProductSummary;
import com.hamas.reviewtrust.domain.products.entity.Product;

/** Product → 公開API DTO 変換。 */
public final class ProductMapper {

    private ProductMapper() {}

    public static ProductSummary toSummary(Product p) {
        return new ProductSummary(p.getId(), p.getAsin(), p.getTitle());
    }

    public static ProductDetail toDetail(Product p) {
        return new ProductDetail(
                p.getId(),
                p.getAsin(),
                p.getTitle(),
                p.getUrl(),
                p.isVisible(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}

