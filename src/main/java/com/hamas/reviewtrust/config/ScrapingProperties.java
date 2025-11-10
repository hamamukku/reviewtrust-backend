package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Playwright を用いたスクレイピング設定項目。
 *
 * <pre>
 * # application-(dev|prod).yml 例
 * app:
 *   scraping:
 *     enabled: true
 *     headless: false
 *     channel: chrome
 *     locale: ja-JP
 *     base-url: https://www.amazon.co.jp
 *     amazon-email: foo@example.com
 *     amazon-password: secret
 *     cookie-string: "session-id=...; at-main=..."
 * </pre>
 */
@ConfigurationProperties(prefix = "app.scraping")
public class ScrapingProperties {

    /** スクレイピング機能の有効 / 無効（既定 true） */
    private boolean enabled = true;

    /** ヘッドレスモードで起動するか（既定 true） */
    private boolean headless = true;

    /**
     * Playwright チャネル（chrome/msedge 等）。空欄ならランタイムの既定値を使用。
     */
    private String channel = "";

    /** Amazon ログイン用メールアドレス（既定 空文字） */
    private String amazonEmail = "";

    /** Amazon ログインパスワード（既定 空文字） */
    private String amazonPassword = "";

    /**
     * 事前ログイン済み Cookie セット（任意）。空文字の場合は利用しない。
     */
    private String cookieString = "";

    /** 既定ロケール（既定 ja-JP） */
    private String locale = "ja-JP";

    /** Amazon ベース URL（既定 https://www.amazon.co.jp） */
    private String baseUrl = "https://www.amazon.co.jp";

    // --- getters / setters ---

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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel == null ? "" : channel.trim();
    }

    public String getAmazonEmail() {
        return amazonEmail;
    }

    public void setAmazonEmail(String amazonEmail) {
        this.amazonEmail = amazonEmail == null ? "" : amazonEmail.trim();
    }

    public String getAmazonPassword() {
        return amazonPassword;
    }

    public void setAmazonPassword(String amazonPassword) {
        this.amazonPassword = amazonPassword == null ? "" : amazonPassword.trim();
    }

    public String getCookieString() {
        return cookieString;
    }

    public void setCookieString(String cookieString) {
        this.cookieString = cookieString == null ? "" : cookieString.trim();
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale == null || locale.isBlank() ? "ja-JP" : locale.trim();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://www.amazon.co.jp"
                : baseUrl.trim();
    }
}
