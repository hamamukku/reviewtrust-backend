// src/main/java/com/hamas/reviewtrust/api/admin/v1/ApiScrapeServiceAdapter.java
package com.hamas.reviewtrust.api.admin.v1;

import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import org.springframework.stereotype.Component;

@Component
public class ApiScrapeServiceAdapter implements AdminProductsController.ScrapeService {

    private final ScrapingService scraping;

    public ApiScrapeServiceAdapter(ScrapingService scraping) {
        this.scraping = scraping;
    }

    @Override
    public AdminProductsController.ScrapeReport rescrape(String productId, String url, int limit) {
        var r = scraping.rescrape(productId, url, limit); // Result: success,count,asin,url,message
        int collected = r.getCount();
        int upserted = collected;     // 暫定：後で実UPSERT件数に合わせて更新
        long durationMs = 0L;         // 暫定：必要なら計測を追加

        return new AdminProductsController.ScrapeReport(collected, upserted, durationMs);
    }
}
