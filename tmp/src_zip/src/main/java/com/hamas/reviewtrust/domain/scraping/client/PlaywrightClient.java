package com.hamas.reviewtrust.domain.scraping.client;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Playwright/Browser をプロセス内で1つ管理。
 * テストでは起動したくないので、プロパティで有効化を切り替える。
 */
@Component
@ConditionalOnProperty(name = "scrape.playwright.enabled", havingValue = "true", matchIfMissing = true)
public class PlaywrightClient {

    private final boolean headless;
    private final String channel;
    private final int slowMoMs;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightClient(
            @Value("${scrape.playwright.headless:true}") boolean headless,
            @Value("${scrape.playwright.channel:}") String channel,
            @Value("${scrape.playwright.slowMoMs:0}") int slowMoMs
    ) {
        this.headless = headless;
        this.channel = channel == null ? "" : channel.trim();
        this.slowMoMs = Math.max(0, slowMoMs);
    }

    @PostConstruct
    public void start() {
        if (playwright != null) return;
        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(headless);
        if (!channel.isBlank()) opts.setChannel(channel);
        if (slowMoMs > 0) opts.setSlowMo((double) slowMoMs);
        browser = playwright.chromium().launch(opts);
    }

    public Browser browser() {
        if (browser == null) start();
        return browser;
    }

    @PreDestroy
    public void stop() {
        try { if (browser != null) browser.close(); } catch (Throwable ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Throwable ignored) {}
        browser = null;
        playwright = null;
    }
}
