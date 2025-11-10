package com.hamas.reviewtrust.scraping;

import com.hamas.reviewtrust.config.AmazonScrapingProperties;

/**
 * Simple helper that resolves Amazon credentials from environment variables or configuration.
 */
public class CredentialProvider {

    private final AmazonScrapingProperties properties;

    public CredentialProvider() {
        this(null);
    }

    public CredentialProvider(AmazonScrapingProperties properties) {
        this.properties = properties;
    }

    public String resolveEmail() {
        String email = firstNonBlank(
                System.getenv("AMAZON_EMAIL"),
                System.getProperty("AMAZON_EMAIL"),
                System.getProperty("scraping.amazon.email"),
                properties != null ? properties.getEmail() : null
        );
        if (email == null) {
            throw new IllegalStateException(
                    "Amazon email credentials are not configured. Set AMAZON_EMAIL or scraping.amazon.email.");
        }
        return email;
    }

    public String resolvePassword() {
        String password = firstNonBlank(
                System.getenv("AMAZON_PASSWORD"),
                System.getProperty("AMAZON_PASSWORD"),
                System.getProperty("scraping.amazon.password"),
                properties != null ? properties.getPassword() : null
        );
        if (password == null) {
            throw new IllegalStateException(
                    "Amazon password credentials are not configured. Set AMAZON_PASSWORD or scraping.amazon.password.");
        }
        return password;
    }

    public String resolveStatePath() {
        String statePath = firstNonBlank(
                System.getenv("AMAZON_STATE_PATH"),
                System.getProperty("AMAZON_STATE_PATH"),
                System.getProperty("scraping.amazon.storageStatePath"),
                properties != null ? properties.getStorageStatePath() : null
        );
        return statePath != null ? statePath : "./var/amazon_state.json";
    }

    private String firstNonBlank(String... options) {
        if (options == null) {
            return null;
        }
        for (String option : options) {
            if (option == null) {
                continue;
            }
            String trimmed = option.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }
}
