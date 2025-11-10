package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser-assisted Amazon scraping configuration.
 */
@ConfigurationProperties(prefix = "scraping.amazon")
public class AmazonScrapingProperties {

    /**
     * Enable Playwright fallback when HTTP scraping hits the Amazon sign-in wall.
     */
    private boolean enableBrowserLogin = false;

    /**
     * Base URL used to construct review pages.
     */
    private String reviewsBase = "https://www.amazon.co.jp/product-reviews";

    /**
     * Path to the Playwright storage state JSON that stores authenticated cookies.
     */
    private String storageStatePath = "./var/amazon_state.json";

    /**
     * Amazon login email. Optional; required only when enableBrowserLogin=true.
     */
    private String email = "";

    /**
     * Amazon login password. Optional; required only when enableBrowserLogin=true.
     */
    private String password = "";

    /**
     * Launch Playwright in headless mode.
     */
    private boolean headless = true;

    /**
     * Default timeout in milliseconds for Playwright interactions.
     */
    private long timeoutMs = 45_000L;

    public boolean isEnableBrowserLogin() {
        return enableBrowserLogin;
    }

    public void setEnableBrowserLogin(boolean enableBrowserLogin) {
        this.enableBrowserLogin = enableBrowserLogin;
    }

    public String getReviewsBase() {
        return reviewsBase;
    }

    public void setReviewsBase(String reviewsBase) {
        this.reviewsBase = reviewsBase;
    }

    public String getStorageStatePath() {
        return storageStatePath;
    }

    public void setStorageStatePath(String storageStatePath) {
        this.storageStatePath = storageStatePath;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

