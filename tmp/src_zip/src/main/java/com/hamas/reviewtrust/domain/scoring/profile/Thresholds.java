package com.hamas.reviewtrust.domain.scoring.profile;

/**
 * Numeric thresholds that individual scoring rules compare against. These
 * values can be tuned based on observed reviewer behaviour to adjust
 * sensitivity. All values are expressed in normalized units (e.g. ratios
 * between 0 and 1 where appropriate).
 */
public class Thresholds {
    /** minimum similarity at which two review texts are considered duplicates */
    public double duplicateTextSimilarity = 0.8;
    /** maximum allowable unnatural language score */
    public double unnaturalLanguageScore = 0.7;
    /** maximum variance in star distribution before considered skewed */
    public double starDistributionVariance = 0.4;
    /** maximum noise ratio allowed in the review text */
    public double noiseRatio = 0.5;
    /** minimum verified purchase rate expected for trustworthy reviewers */
    public double verifiedPurchaseRate = 0.5;
    /** minimum number of days a reviewer account should exist before trust */
    public int accountAgeDays = 30;
    /** maximum number of reviews a user can post in 24h before flagged */
    public int maxReviewsPerDay = 10;
    /** maximum concentration of reviews on a single vendor */
    public double vendorConcentration = 0.8;
    /** minimum specificity score expected in the review text */
    public double specificityScore = 0.3;
    /** maximum similarity threshold for detecting new account bursts */
    public int newAccountAgeDays = 7;
    /** number of reviews in 24h considered a burst for new accounts */
    public int newAccountBurstReviews = 3;
}