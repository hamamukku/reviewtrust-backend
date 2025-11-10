package com.hamas.reviewtrust.domain.scraping.scheduler;

import com.hamas.reviewtrust.domain.scraping.parser.AmazonReviewParser.ReviewItem;
import com.hamas.reviewtrust.domain.scraping.service.AmazonScrapeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(value = "app.scraping.dev-runner.enabled", havingValue = "true", matchIfMissing = false)
public class DevScrapeRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevScrapeRunner.class);

    private final AmazonScrapeService service;

    @Value("${app.scraping.dev-runner.url:}")
    private String targetUrl;

    @Value("${app.scraping.dev-runner.limit:10}")
    private int limit;

    public DevScrapeRunner(AmazonScrapeService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (targetUrl == null || targetUrl.isBlank()) {
            log.info("[DevScrapeRunner] targetUrl is empty. Skip.");
            return;
        }
        List<ReviewItem> items = service.preview(targetUrl, limit);
        log.info("[DevScrapeRunner] scraped {} reviews", items.size());
        items.stream().limit(Math.max(1, Math.min(5, limit))).forEach(it ->
            log.info("- {}★ [{}] {} (helpful={})",
                    it.getRating(),
                    it.getReviewer(),
                    it.getTitle(),
                    it.getHelpfulVotes())
        );
    }
}
