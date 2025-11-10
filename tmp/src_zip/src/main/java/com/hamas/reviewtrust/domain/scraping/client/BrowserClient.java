package com.hamas.reviewtrust.domain.scraping.client;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnBean(PlaywrightClient.class) // ← Playwright が有効なときだけ本番版を作る
public class BrowserClient {

    protected final PlaywrightClient pw;
    private final int navTimeoutMs = 25_000;
    private final String defaultUA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public BrowserClient(PlaywrightClient pw) {
        this.pw = pw;
    }

    public String fetchHtml(String url, Locale locale, String userAgent) {
        Browser browser = pw.browser();
        Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                .setUserAgent(userAgent != null && !userAgent.isBlank() ? userAgent : defaultUA)
                .setLocale(locale != null ? locale.toLanguageTag() : "ja-JP")
                .setViewportSize(1280, 1600);

        try (BrowserContext ctx = browser.newContext(ctxOpts);
             Page page = ctx.newPage()) {

            page.setDefaultNavigationTimeout(navTimeoutMs);
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout((double) navTimeoutMs));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2_000);
            return page.content();
        }
    }
}
