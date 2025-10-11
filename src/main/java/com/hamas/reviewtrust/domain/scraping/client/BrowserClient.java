// BrowserClient.java (placeholder)
package com.hamas.reviewtrust.domain.scraping.client;

/**
 * 動的ページ対応を前提にしたブラウザ取得IF。
 * 本番では Playwright/Puppeteer 等で実装。MVPはHTTPフォールバックの仮実装でも可。
 */
public interface BrowserClient {

    /** 静的/軽量ページ向けの最小取得。 */
    String getHtml(String url) throws Exception;

    /** 取得前の待機ミリ秒を指定できる版。 */
    String getHtml(String url, int waitMillis) throws Exception;

    /**
     * スクロール等が必要なページ向けの取得。
     * MVP仮実装では静的取得で代替し、将来Playwrightへ差し替える。
     */
    String scrollAndGet(String url, int maxScrolls, int delayMs) throws Exception;
}
