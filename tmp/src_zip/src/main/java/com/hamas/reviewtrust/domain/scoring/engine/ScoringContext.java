package com.hamas.reviewtrust.domain.scoring.engine;

/**
 * Aggregates features about a review and its author used by the scoring rules.
 * A context can be constructed via its builder. All values are normalized
 * where applicable (e.g. ratios between 0 and 1).
 */
public class ScoringContext {
    private final String reviewText;
    private final int starRating;
    private final boolean verifiedPurchase;
    private final boolean anonymous;
    private final double userVerifiedPurchaseRate;
    private final int userAccountAgeDays;
    private final int userReviewsLast24h;
    private final double starDistributionVariance;
    private final double vendorConcentration;
    private final double noiseRatio;
    private final double specificityScore;
    private final double unnaturalLanguageScore;
    private final double duplicateTextSimilarity;

    private ScoringContext(Builder b) {
        this.reviewText = b.reviewText;
        this.starRating = b.starRating;
        this.verifiedPurchase = b.verifiedPurchase;
        this.anonymous = b.anonymous;
        this.userVerifiedPurchaseRate = b.userVerifiedPurchaseRate;
        this.userAccountAgeDays = b.userAccountAgeDays;
        this.userReviewsLast24h = b.userReviewsLast24h;
        this.starDistributionVariance = b.starDistributionVariance;
        this.vendorConcentration = b.vendorConcentration;
        this.noiseRatio = b.noiseRatio;
        this.specificityScore = b.specificityScore;
        this.unnaturalLanguageScore = b.unnaturalLanguageScore;
        this.duplicateTextSimilarity = b.duplicateTextSimilarity;
    }

    public String getReviewText() { return reviewText; }
    public int getStarRating() { return starRating; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public boolean isAnonymous() { return anonymous; }
    public double getUserVerifiedPurchaseRate() { return userVerifiedPurchaseRate; }
    public int getUserAccountAgeDays() { return userAccountAgeDays; }
    public int getUserReviewsLast24h() { return userReviewsLast24h; }
    public double getStarDistributionVariance() { return starDistributionVariance; }
    public double getVendorConcentration() { return vendorConcentration; }
    public double getNoiseRatio() { return noiseRatio; }
    public double getSpecificityScore() { return specificityScore; }
    public double getUnnaturalLanguageScore() { return unnaturalLanguageScore; }
    public double getDuplicateTextSimilarity() { return duplicateTextSimilarity; }

    public static class Builder {
        private String reviewText = "";
        private int starRating = 0;
        private boolean verifiedPurchase = false;
        private boolean anonymous = false;
        private double userVerifiedPurchaseRate = 1.0;
        private int userAccountAgeDays = 365;
        private int userReviewsLast24h = 0;
        private double starDistributionVariance = 0.0;
        private double vendorConcentration = 0.0;
        private double noiseRatio = 0.0;
        private double specificityScore = 1.0;
        private double unnaturalLanguageScore = 0.0;
        private double duplicateTextSimilarity = 0.0;

        public Builder reviewText(String val) { this.reviewText = val; return this; }
        public Builder starRating(int val) { this.starRating = val; return this; }
        public Builder verifiedPurchase(boolean val) { this.verifiedPurchase = val; return this; }
        public Builder anonymous(boolean val) { this.anonymous = val; return this; }
        public Builder userVerifiedPurchaseRate(double val) { this.userVerifiedPurchaseRate = val; return this; }
        public Builder userAccountAgeDays(int val) { this.userAccountAgeDays = val; return this; }
        public Builder userReviewsLast24h(int val) { this.userReviewsLast24h = val; return this; }
        public Builder starDistributionVariance(double val) { this.starDistributionVariance = val; return this; }
        public Builder vendorConcentration(double val) { this.vendorConcentration = val; return this; }
        public Builder noiseRatio(double val) { this.noiseRatio = val; return this; }
        public Builder specificityScore(double val) { this.specificityScore = val; return this; }
        public Builder unnaturalLanguageScore(double val) { this.unnaturalLanguageScore = val; return this; }
        public Builder duplicateTextSimilarity(double val) { this.duplicateTextSimilarity = val; return this; }
        public ScoringContext build() { return new ScoringContext(this); }
    }
}