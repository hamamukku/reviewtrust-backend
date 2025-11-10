package com.hamas.reviewtrust.domain.scraping.scheduler;

import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(value = "app.scraping.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class RescrapeScheduler {
    private static final Logger log = LoggerFactory.getLogger(RescrapeScheduler.class);
    private final ScrapingService service;

    public RescrapeScheduler(ScrapingService service) {
        this.service = service;
    }

    /**
     * 毎日 03:00（サーバローカル時刻）に実行。
     * 実運用では ASIN リストを設定/DB/環境変数から読む構成に寄せる。
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void nightlyHealthcheck() {
        log.debug("[RescrapeScheduler] enabled. Configure your ASIN list to rescrape if needed.");
        // 例：サンプル実行（コメントアウトを外す）
        // service.scrapeAmazonByAsin("B00EXAMPLE", Locale.JAPAN, 20);
        // service.scrapeAmazonByUrl("https://www.amazon.co.jp/product-reviews/B00EXAMPLE/?reviewerType=all_reviews&sortBy=recent", 20);
    }
}
