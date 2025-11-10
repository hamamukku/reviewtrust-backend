package com.hamas.reviewtrust.scraping;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration backing the Playwright-based Amazon scraping runner.
 */
@ConfigurationProperties(prefix = "app.scraping")
public class ScrapingProps {

    /**
     * Enable or disable the scraping runner entirely.
     */
    private boolean enabled = true;

    /**
     * Launch Playwright in headless mode (default false to allow first-run login).
     */
    private boolean headless = false;

    /**
     * Allow interactive Playwright login to bootstrap storage state.
     */
    private boolean enableBrowserLogin = true;

    /**
     * Destination of the Playwright storageState JSON that keeps the Amazon session.
     */
    private String storageStatePath = "build/amazon-storage.json";

    /**
     * Amazon login email/username.
     */
    private String amazonEmail = "";

    /**
     * Amazon login password.
     */
    private String amazonPassword = "";

    /**
     * Optional Playwright browser channel (e.g. {@code chrome}, {@code msedge}).
     */
    private String channel = "";

    /**
     * CSV file containing a column named {@code url}. Defaults to ../data/urls.csv.
     */
    private String dataCsvPath = "../data/urls.csv";

    /**
     * Output directory (relative or absolute path).
     */
    private String outDir = "delivery/proof";

    /**
     * Output format: ndjson|json|csv.
     */
    private String outFormat = "ndjson";

    /**
     * Write one file per ASIN (true) or append per dataset (false).
     */
    private boolean outPerProduct = true;

    /**
     * Emit empty files even when scrape returns zero reviews.
     */
    private boolean writeNoReviews = false;

    /**
     * Prepend UTF-8 BOM when writing CSV (Excel compatibility).
     */
    private boolean csvWithBom = true;

    /**
     * Explicit dataset label (fallback to inference when blank).
     */
    private String datasetLabel = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public boolean isEnableBrowserLogin() {
        return enableBrowserLogin;
    }

    public void setEnableBrowserLogin(boolean enableBrowserLogin) {
        this.enableBrowserLogin = enableBrowserLogin;
    }

    public String getStorageStatePath() {
        return storageStatePath;
    }

    public void setStorageStatePath(String storageStatePath) {
        this.storageStatePath = storageStatePath;
    }

    public String getAmazonEmail() {
        return amazonEmail;
    }

    public void setAmazonEmail(String amazonEmail) {
        this.amazonEmail = amazonEmail;
    }

    public String getAmazonPassword() {
        return amazonPassword;
    }

    public void setAmazonPassword(String amazonPassword) {
        this.amazonPassword = amazonPassword;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel == null ? "" : channel.trim();
    }

    public String getDataCsvPath() {
        return dataCsvPath;
    }

    public void setDataCsvPath(String dataCsvPath) {
        this.dataCsvPath = dataCsvPath;
    }

    public String getOutDir() {
        return outDir;
    }

    public void setOutDir(String outDir) {
        this.outDir = outDir == null ? "" : outDir.trim();
    }

    public String getOutFormat() {
        return outFormat;
    }

    public void setOutFormat(String outFormat) {
        this.outFormat = outFormat == null ? "" : outFormat.trim();
    }

    public boolean isOutPerProduct() {
        return outPerProduct;
    }

    public void setOutPerProduct(boolean outPerProduct) {
        this.outPerProduct = outPerProduct;
    }

    public boolean isWriteNoReviews() {
        return writeNoReviews;
    }

    public void setWriteNoReviews(boolean writeNoReviews) {
        this.writeNoReviews = writeNoReviews;
    }

    public boolean isCsvWithBom() {
        return csvWithBom;
    }

    public void setCsvWithBom(boolean csvWithBom) {
        this.csvWithBom = csvWithBom;
    }

    public String getDatasetLabel() {
        return datasetLabel;
    }

    public void setDatasetLabel(String datasetLabel) {
        this.datasetLabel = datasetLabel == null ? "" : datasetLabel.trim();
    }
}
