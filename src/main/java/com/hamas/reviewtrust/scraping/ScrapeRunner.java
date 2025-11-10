package com.hamas.reviewtrust.scraping;

import com.hamas.reviewtrust.scraping.io.ScrapeResultWriter;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * CLI-oriented runner that boots Playwright, iterates URLs, and verifies review presence.
 */
@Component
public class ScrapeRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ScrapeRunner.class);

    private final ScrapingProps props;
    private final CsvUrlIterator csvUrlIterator;
    private final AmazonReviewScraper scraper;
    private final ScrapeResultWriter resultWriter;
    private final boolean autoIntakeEnabled;
    private final String activeProfiles;

    public ScrapeRunner(ScrapingProps props,
                        CsvUrlIterator csvUrlIterator,
                        AmazonReviewScraper scraper,
                        ScrapeResultWriter resultWriter,
                        @Value("${scraping.intake.auto:true}") boolean autoIntakeEnabled,
                        @Value("${spring.profiles.active:}") String activeProfiles) {
        this.props = props;
        this.csvUrlIterator = csvUrlIterator;
        this.scraper = scraper;
        this.resultWriter = resultWriter;
        this.autoIntakeEnabled = autoIntakeEnabled;
        this.activeProfiles = activeProfiles;
    }

    @Override
    public void run(String... args) {
        if (!props.isEnabled()) {
            log.info("event=SCRAPE_DISABLED message=app.scraping.enabled=false");
            return;
        }

        if (!autoIntakeEnabled) {
            log.info("ScrapeRunner - Skipping CSV auto intake (scraping.intake.auto=false)");
            return;
        }

        Path logPath = ensureLogFile();
        Path csvPath = resolveCsvPath(props.getDataCsvPath());
        List<String> urls = csvUrlIterator.loadUrls(csvPath).toList();
        if (urls.isEmpty()) {
            log.warn("event=SCRAPE_SKIPPED reason=no_urls path={}", csvPath);
            return;
        }

        executeScrape(urls, csvPath, logPath);
    }

    private void executeScrape(List<String> urls, Path csvPath, Path logPath) {
        log.info("event=SCRAPE_START urls={} csv={} logFile={}", urls.size(), csvPath, logPath);
        int success = 0;
        int failed = 0;
        long started = System.nanoTime();
        String dataset = resolveDatasetLabel(props);
        boolean devProfileActive = isDevProfileActive();
        boolean allowInteractiveLogin = devProfileActive && props.isEnableBrowserLogin();
        if (!devProfileActive) {
            log.info("ScrapeRunner - Skipping GUI login (profile != dev)");
        } else if (!props.isEnableBrowserLogin()) {
            log.info("ScrapeRunner - Skipping GUI login (scraping.enableBrowserLogin=false)");
        }

        try (AmazonBrowserClient client = new AmazonBrowserClient(props)) {
            client.openContext(allowInteractiveLogin);
            Page page = client.getPage();
            for (String url : urls) {
                AmazonReviewScraper.Result result = scraper.scrapeOne(page, url);
                if (result.success()) {
                    success++;
                    Instant capturedAt = result.capturedAt() != null ? result.capturedAt() : Instant.now();
                    try {
                        Path out = resultWriter.writeHistogramMeta(
                                dataset, url, result.histogram(), capturedAt, result.productName());
                        if (!result.reviews().isEmpty() || props.isWriteNoReviews()) {
                            resultWriter.write(dataset, url, result.reviews(), null, capturedAt);
                        }
                        log.info("event=WRITE_RESULT dataset={} url={} count={} path={}",
                                dataset, url, result.reviews().size(), out.toAbsolutePath());
                    } catch (Exception e) {
                        log.warn("event=WRITE_RESULT_FAILED dataset={} url={} message={}",
                                dataset, url, e.getMessage(), e);
                    }
                } else {
                    failed++;
                    if (props.isWriteNoReviews() && isNoReviews(result.reason())) {
                        try {
                            Instant capturedAt = Instant.now();
                            Path out = resultWriter.writeHistogramMeta(
                                    dataset, url, null, capturedAt, result.productName());
                            resultWriter.write(dataset, url, Collections.emptyList(), null, capturedAt);
                            log.info("event=WRITE_RESULT dataset={} url={} count=0 path={}",
                                    dataset, url, out.toAbsolutePath());
                        } catch (Exception e) {
                            log.warn("event=WRITE_RESULT_FAILED dataset={} url={} message={}",
                                    dataset, url, e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("event=SCRAPE_ABORTED message={}", e.getMessage(), e);
            throw e;
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("event=SCRAPE_COMPLETED success={} failed={} durationMs={}", success, failed, durationMs);
        }
    }

    private Path resolveCsvPath(String configured) {
        Path path = Path.of(configured == null || configured.isBlank() ? "../data/urls.csv" : configured);
        return path.toAbsolutePath().normalize();
    }

    private Path ensureLogFile() {
        String configured = System.getProperty("logging.file.name");
        if (configured == null || configured.isBlank()) {
            configured = "build/logs/app.log";
        }
        Path path = Path.of(configured).toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            log.warn("event=LOG_FILE_PREP_FAILED path={} message={}", path, e.getMessage());
        }
        return path;
    }

    private static boolean isNoReviews(String reason) {
        return reason != null && reason.equalsIgnoreCase("no_reviews");
    }

    private static String resolveDatasetLabel(ScrapingProps props) {
        String explicit = safe(props.getDatasetLabel());
        if (!explicit.isBlank()) {
            return slug(explicit);
        }
        String fromEnv = safe(System.getenv("DATA_CSV_PATH"));
        if (!fromEnv.isBlank()) {
            Path p = Paths.get(fromEnv);
            String fileName = p.getFileName().toString().replaceFirst("\\.csv$", "");
            return slug(fileName);
        }
        return "adhoc";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String slug(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "adhoc" : normalized;
    }

    private boolean isDevProfileActive() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev"));
    }
}
