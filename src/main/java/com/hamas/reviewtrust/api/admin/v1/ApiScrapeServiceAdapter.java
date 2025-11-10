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
        var result = scraping.rescrape(productId, url, limit);
        return new AdminProductsController.ScrapeReport(
                result.getCollected(),
                result.getUpserted(),
                result.getDurationMs()
        );
    }
}
