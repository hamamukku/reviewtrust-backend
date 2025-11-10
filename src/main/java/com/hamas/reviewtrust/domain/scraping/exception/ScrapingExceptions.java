package com.hamas.reviewtrust.domain.scraping.exception;

/**
 * スクレイピング専用の例外生成ユーティリティ。
 * - コード文字列は APIのエラーフォーマット {error:{code,...}} と整合（MVP）
 * - コントローラ層では ResponseStatusException 等へ包み直し、例外ログに残す
 */
public final class ScrapingExceptions {

    private ScrapingExceptions() { }

    public static class ScrapeException extends RuntimeException {
        private final String code; // 例: E_SCRAPE_TIMEOUT
        public ScrapeException(String code, String message) { super(message); this.code = code; }
        public ScrapeException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String getCode() { return code; }
    }

    public static ScrapeException timeout(String msg) {
        return new ScrapeException("E_SCRAPE_TIMEOUT", msg);
    }

    public static ScrapeException timeout(String msg, Throwable cause) {
        return new ScrapeException("E_SCRAPE_TIMEOUT", msg, cause);
    }

    public static ScrapeException blocked(String msg) {
        return new ScrapeException("E_SCRAPE_BLOCKED", msg);
    }

    public static ScrapeException selectorChanged(String msg) {
        return new ScrapeException("E_SCRAPE_SELECTOR_CHANGED", msg);
    }

    public static ScrapeException network(String msg, Throwable cause) {
        return new ScrapeException("E_SCRAPE_NETWORK", msg, cause);
    }

    public static ScrapeException failed(String msg, Throwable cause) {
        return new ScrapeException("E_SCRAPE_FAILED", msg, cause);
    }
}

