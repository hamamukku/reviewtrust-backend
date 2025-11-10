package com.hamas.reviewtrust.testsupport;

import com.hamas.reviewtrust.domain.scraping.client.BrowserClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * テスト専用 BrowserClient。
 * Playwright を起動せず、固定HTMLを返すスタブ。
 */
@Primary
@Component
public class TestBrowserClient extends BrowserClient {
    public TestBrowserClient() { super(null); }

    @Override
    public String fetchHtml(String url, Locale locale, String userAgent) {
        return """
            <html><body>
              <div data-hook="review" id="R1">
                <span data-hook="review-star-rating"><span class="a-icon-alt">4.0 out of 5 stars</span></span>
                <span data-hook="review-date">June 3, 2024</span>
                <span data-hook="genome-widget"><span class="a-profile-name">Alice</span></span>
                <span data-hook="review-body">良い商品でした。とても満足。</span>
              </div>
            </body></html>
            """;
    }
}
