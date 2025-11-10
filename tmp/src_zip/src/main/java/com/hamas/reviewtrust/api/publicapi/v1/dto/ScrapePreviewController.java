package com.hamas.reviewtrust.api.publicapi.v1.dto;

import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser;
import com.hamas.reviewtrust.domain.scraping.service.ScrapingService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/v1/scrape/preview")
public class ScrapePreviewController {

    private final ScrapingService scrapingService;

    public ScrapePreviewController(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    /**
     * 例: /api/public/v1/scrape/preview/url?url=...&limit=10
     */
    @GetMapping("/url")
    public ResponseEntity<PreviewResponse> previewByUrl(
            @RequestParam("url") String url,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        if (!StringUtils.hasText(url)) {
            return ResponseEntity.badRequest().build();
        }
        List<AmazonReviewParser.ReviewItem> items = scrapingService.scrapeAmazonByUrl(url, limit);
        return ResponseEntity.ok(new PreviewResponse(items, items.size()));
    }

    /**
     * 例: /api/public/v1/scrape/preview/asin?asin=B0XXXXXXX&locale=ja-JP&limit=10
     */
    @GetMapping("/asin")
    public ResponseEntity<PreviewResponse> previewByAsin(
            @RequestParam("asin") String asin,
            @RequestParam(name = "locale", defaultValue = "ja-JP") String localeTag,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        if (!StringUtils.hasText(asin)) {
            return ResponseEntity.badRequest().build();
        }
        Locale locale = Locale.forLanguageTag(localeTag);
        List<AmazonReviewParser.ReviewItem> items = scrapingService.scrapeAmazonByAsin(asin, locale, limit);
        return ResponseEntity.ok(new PreviewResponse(items, items.size()));
    }

    public record PreviewResponse(List<AmazonReviewParser.ReviewItem> items, int count) {}
}
