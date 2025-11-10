package com.hamas.reviewtrust.tools;

import com.hamas.reviewtrust.scraping.AmazonBrowserScraper;
import com.hamas.reviewtrust.scraping.AmazonBrowserScraper.ReviewsResult;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI helper to seed the Amazon Playwright storage state. Run this once with AMAZON_EMAIL /
 * AMAZON_PASSWORD exported and complete the login interactively.
 */
public final class AmazonLoginSeed {

    private AmazonLoginSeed() {
    }

    public static void main(String[] args) {
        String asin = args.length > 0 ? args[0] : "B09NB8HYWH";
        Path statePath = Paths.get("var/amazon_state.json");

        try (Playwright playwright = Playwright.create()) {
            AmazonBrowserScraper scraper = new AmazonBrowserScraper(playwright, false, statePath);
            try {
                System.out.println("Opening Amazon product reviews page for ASIN: " + asin);
                System.out.println("Complete the login in the launched browser. Solve CAPTCHA/MFA if prompted.");
                ReviewsResult result = scraper.fetchReviewsHtml(asin, true, statePath);
                String html = result.getReviewsHtml();
                if (html != null) {
                    System.out.println("Seed complete. Storage state saved to: " + statePath.toAbsolutePath());
                    System.out.println("Fetched HTML length = " + html.length());
                } else if (result.getProductPageHtml() != null) {
                    System.out.println("Fallback product page captured: " + result.getProductPageUrl());
                } else {
                    System.out.println("No HTML captured; review page may still be blocked.");
                }
            } finally {
                scraper.close();
            }
        }
    }
}
