package com.hamas.reviewtrust.domain.products.service;

import com.hamas.reviewtrust.api.publicapi.v1.dto.ProductScoreResponse;
import com.hamas.reviewtrust.domain.products.repo.ProductSnapshotRepository;
import com.hamas.reviewtrust.domain.products.repo.ProductSnapshotRepository.SnapshotRow;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service("productSnapshotScoreService")
public class ProductScoreService {

    private final ProductSnapshotRepository productSnapshotRepository;

    public ProductScoreService(ProductSnapshotRepository productSnapshotRepository) {
        this.productSnapshotRepository = productSnapshotRepository;
    }

    public Optional<ProductScoreResponse> findLatestByAsin(String asin) {
        if (!StringUtils.hasText(asin)) {
            return Optional.empty();
        }
        return productSnapshotRepository.findLatestByAsin(asin.trim())
                .map(this::toResponse);
    }

    private ProductScoreResponse toResponse(SnapshotRow row) {
        ProductPageSnapshot snapshot = row.snapshot();
        Double averageScore = snapshot != null ? snapshot.getRatingAverage() : null;
        Integer reviewCount = snapshot != null ? toInteger(snapshot.getRatingCount()) : null;
        Map<Integer, Long> histogram = snapshot != null ? buildHistogram(snapshot) : Map.of();

        String productName = firstNonBlank(
                row.productName(),
                snapshot != null ? snapshot.getTitle() : null,
                row.title(),
                row.asin()
        );
        ProductScoreResponse response = new ProductScoreResponse();
        response.setProductId(row.productId());
        response.setAsin(row.asin());
        response.setProductName(productName);
        response.setAverageScore(averageScore);
        response.setReviewCount(reviewCount);
        response.setHistogram(histogram);
        response.setAmazon(buildAmazonScore(averageScore));
        return response;
    }

    private Integer toInteger(Long value) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return value.intValue();
    }

    private Map<Integer, Long> buildHistogram(ProductPageSnapshot snapshot) {
        Map<Integer, Double> share = snapshot.getRatingSharePct();
        if (share == null || share.isEmpty()) {
            return Map.of();
        }
        long total = snapshot.getRatingCount() != null ? Math.max(0, snapshot.getRatingCount()) : 0L;
        Map<Integer, Long> histogram = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            double pct = share.getOrDefault(star, 0.0d);
            double safePct = Double.isNaN(pct) ? 0.0d : Math.max(0.0d, pct);
            long value = total > 0
                    ? Math.round((safePct / 100.0d) * total)
                    : Math.round(safePct);
            histogram.put(star, Math.max(0, value));
        }
        return histogram;
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private ProductScoreResponse.ScoreBlock buildAmazonScore(Double ratingAverage) {
        Double score = ratingAverage != null ? convertToPercent(ratingAverage) : null;
        if (score == null) {
            return null;
        }
        ProductScoreResponse.ScoreBlock block = new ProductScoreResponse.ScoreBlock();
        block.setScore(score);
        block.setDisplayScore(score);
        block.setRank(rankForScore(score));
        return block;
    }

    private Double convertToPercent(Double ratingAverage) {
        if (ratingAverage == null || ratingAverage.isNaN()) {
            return null;
        }
        double clamped = Math.max(0.0d, Math.min(5.0d, ratingAverage));
        double scaled = (clamped / 5.0d) * 100.0d;
        return Math.round(scaled * 10.0d) / 10.0d;
    }

    private String rankForScore(Double score) {
        if (score == null) {
            return null;
        }
        if (score < 35.0d) {
            return "A";
        }
        if (score < 65.0d) {
            return "B";
        }
        return "C";
    }
}
