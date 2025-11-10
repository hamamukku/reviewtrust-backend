package com.hamas.reviewtrust.domain.scraping.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Normalised representation of an Amazon detail page that can be persisted as a snapshot.
 */
public final class ProductPageSnapshot {

    private final String asin;
    private final String title;
    private final String brand;
    private final Long priceMinor;
    private final Double ratingAverage;
    private final Long ratingCount;
    private final Map<Integer, Double> ratingSharePct;
    private final List<String> imageUrls;
    private final List<String> featureBullets;
    private final List<InlineReview> inlineReviews;
    private final boolean partial;
    private final Instant capturedAt;

    private ProductPageSnapshot(Builder builder) {
        this.asin = builder.asin;
        this.title = builder.title;
        this.brand = builder.brand;
        this.priceMinor = builder.priceMinor;
        this.ratingAverage = builder.ratingAverage;
        this.ratingCount = builder.ratingCount;
        if (builder.ratingSharePct == null || builder.ratingSharePct.isEmpty()) {
            this.ratingSharePct = Map.of();
        } else {
            this.ratingSharePct = Collections.unmodifiableMap(new LinkedHashMap<>(builder.ratingSharePct));
        }
        if (builder.imageUrls == null || builder.imageUrls.isEmpty()) {
            this.imageUrls = List.of();
        } else {
            this.imageUrls = List.copyOf(builder.imageUrls);
        }
        if (builder.featureBullets == null || builder.featureBullets.isEmpty()) {
            this.featureBullets = List.of();
        } else {
            this.featureBullets = List.copyOf(builder.featureBullets);
        }
        if (builder.inlineReviews == null || builder.inlineReviews.isEmpty()) {
            this.inlineReviews = List.of();
        } else {
            this.inlineReviews = List.copyOf(builder.inlineReviews);
        }
        this.partial = builder.partial;
        this.capturedAt = Objects.requireNonNullElseGet(builder.capturedAt, Instant::now);
    }

    public String getAsin() {
        return asin;
    }

    public String getTitle() {
        return title;
    }

    public String getBrand() {
        return brand;
    }

    public Long getPriceMinor() {
        return priceMinor;
    }

    public Double getRatingAverage() {
        return ratingAverage;
    }

    public Long getRatingCount() {
        return ratingCount;
    }

    public Map<Integer, Double> getRatingSharePct() {
        return ratingSharePct;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public List<String> getFeatureBullets() {
        return featureBullets;
    }

    public List<InlineReview> getInlineReviews() {
        return inlineReviews;
    }

    public boolean isPartial() {
        return partial;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class InlineReview {
        private final String title;
        private final String body;
        private final Integer stars;
        private final Boolean verified;
        private final String dateText;

        public InlineReview(String title,
                            String body,
                            Integer stars,
                            Boolean verified,
                            String dateText) {
            this.title = title;
            this.body = body;
            this.stars = stars;
            this.verified = verified;
            this.dateText = dateText;
        }

        public String getTitle() {
            return title;
        }

        public String getBody() {
            return body;
        }

        public Integer getStars() {
            return stars;
        }

        public Boolean getVerified() {
            return verified;
        }

        public String getDateText() {
            return dateText;
        }
    }

    public static final class Builder {
        private String asin;
        private String title;
        private String brand;
        private Long priceMinor;
        private Double ratingAverage;
        private Long ratingCount;
        private Map<Integer, Double> ratingSharePct;
        private List<String> imageUrls;
        private List<String> featureBullets;
        private List<InlineReview> inlineReviews;
        private boolean partial;
        private Instant capturedAt;

        private Builder() {
        }

        public Builder asin(String asin) {
            this.asin = asin;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public Builder priceMinor(Long priceMinor) {
            this.priceMinor = priceMinor;
            return this;
        }

        public Builder ratingAverage(Double ratingAverage) {
            this.ratingAverage = ratingAverage;
            return this;
        }

        public Builder ratingCount(Long ratingCount) {
            this.ratingCount = ratingCount;
            return this;
        }

        public Builder ratingSharePct(Map<Integer, Double> ratingSharePct) {
            this.ratingSharePct = ratingSharePct;
            return this;
        }

        public Builder imageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
            return this;
        }

        public Builder featureBullets(List<String> featureBullets) {
            this.featureBullets = featureBullets;
            return this;
        }

        public Builder inlineReviews(List<InlineReview> inlineReviews) {
            this.inlineReviews = inlineReviews;
            return this;
        }

        public Builder partial(boolean partial) {
            this.partial = partial;
            return this;
        }

        public Builder capturedAt(Instant capturedAt) {
            this.capturedAt = capturedAt;
            return this;
        }

        public ProductPageSnapshot build() {
            return new ProductPageSnapshot(this);
        }
    }
}

