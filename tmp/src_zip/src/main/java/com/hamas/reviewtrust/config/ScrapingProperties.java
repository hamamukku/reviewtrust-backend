package com.hamas.reviewtrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Playwright を用いたスクレイピングの実行設定。
 *
 * <pre>
 * # application-(dev|prod).yml 例
 * app:
 *   scraping:
 *     enabled: true
 *     headless: false
 *     channel: chrome   # 任意（指定しなければ既定ブラウザ）
 *     locale: ja-JP
 *     baseUrl: https://www.amazon.co.jp
 *     amazonEmail: ...
 *     amazonPassword: ...
 *     cookieString: "session-id=...; at-main=..."
 * </pre>
 *
 * なお、ヘッドレス切替は JVM 引数でも上書き可能:
 *   -Dapp.scraping.headless=false
 */
@Component
@ConfigurationProperties(prefix = "app.scraping")
public class ScrapingProperties {

    /** スクレイピング機能の有効/無効（既定 true） */
    private boolean enabled = true;

    /** ヘッドレスで動かすか（既定 true） */
    private boolean headless = true;

    /**
     * ブラウザチャネル（例: "chrome", "msedge" など）。
     * 未指定なら Playwright の既定を利用。
     */
    private String channel;

    /** Amazon ログイン用メール（任意） */
    private String amazonEmail;

    /** Amazon ログイン用パスワード（任意） */
    private String amazonPassword;

    /**
     * 事前に投下する Cookie 文字列（例: "session-id=...; ubid-acbjp=...; at-main=..."）
     * ログイン回避の検証用途向け（本番では推奨しない）。
     */
    private String cookieString;

    /** デフォルト言語ロケール（既定 "ja-JP"） */
    private String locale = "ja-JP";

    /** Amazon ベース URL（既定 "https://www.amazon.co.jp"） */
    private String baseUrl = "https://www.amazon.co.jp";

    // --- getters / setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getAmazonEmail() { return amazonEmail; }
    public void setAmazonEmail(String amazonEmail) { this.amazonEmail = amazonEmail; }

    public String getAmazonPassword() { return amazonPassword; }
    public void setAmazonPassword(String amazonPassword) { this.amazonPassword = amazonPassword; }

    public String getCookieString() { return cookieString; }
    public void setCookieString(String cookieString) { this.cookieString = cookieString; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
