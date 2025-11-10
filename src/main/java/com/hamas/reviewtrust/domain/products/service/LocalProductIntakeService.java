package com.hamas.reviewtrust.domain.products.service;

import com.hamas.reviewtrust.config.LocalIntakeProperties;
import com.hamas.reviewtrust.domain.products.repo.ProductSnapshotRepository;
import com.hamas.reviewtrust.domain.products.repo.ProductSnapshotRepository.SnapshotRow;
import com.hamas.reviewtrust.domain.scraping.client.BrowserClient;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Profile("!dev")
public class LocalProductIntakeService {

    private final BrowserClient browserClient;
    private final ProductIntakeService productIntakeService;
    private final ProductSnapshotRepository snapshotRepository;
    private final LocalIntakeProperties properties;
    private final Clock clock;

    public LocalProductIntakeService(BrowserClient browserClient,
                                     ProductIntakeService productIntakeService,
                                     ProductSnapshotRepository snapshotRepository,
                                     LocalIntakeProperties properties,
                                     Clock clock) {
        this.browserClient = browserClient;
        this.productIntakeService = productIntakeService;
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public ProductIntakeService.Result ingestUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url is required");
        }
        String html = browserClient.fetchHtml(url.trim(), Locale.JAPAN, null);
        if (html == null || html.isBlank() || looksBlocked(html)) {
            throw new IllegalStateException("Fetched HTML appears to be blocked for url=" + url);
        }
        return productIntakeService.registerOrUpdateFromHtml(html, url);
    }

    public CsvIngestResult ingestCsv(@Nullable Path overridePath) {
        Path csv = overridePath != null ? overridePath : properties.getCsvPath();
        if (csv == null) {
            throw new IllegalArgumentException("csv path is not configured");
        }
        if (!Files.exists(csv)) {
            return new CsvIngestResult(0, 0, List.of(), List.of(), csv);
        }

        List<ItemResult> successes = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        int processed = 0;
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            for (String line : lines) {
                String url = extractUrl(line);
                if (url == null) {
                    continue;
                }
                processed++;
                try {
                    ProductIntakeService.Result result = ingestUrl(url);
                    successes.add(new ItemResult(result.product().getId(), result.snapshot().getAsin(), result.created()));
                } catch (Exception ex) {
                    failures.add(new Failure(url, ex.getMessage()));
                }
            }
        } catch (Exception e) {
            failures.add(new Failure(csv.toString(), "Failed to read CSV: " + e.getMessage()));
        }

        return new CsvIngestResult(processed, successes.size(), successes, failures, csv);
    }

    public SnapshotList listSnapshots(boolean includeUploaded, int limit) {
        List<SnapshotRow> rows = snapshotRepository.findRecent(limit, includeUploaded);
        List<SnapshotSummary> summaries = new ArrayList<>(rows.size());
        for (SnapshotRow row : rows) {
            ProductPageSnapshot snapshot = row.snapshot();
            summaries.add(new SnapshotSummary(
                    row.snapshotId(),
                    row.asin(),
                    snapshot.getTitle(),
                    snapshot.getPriceMinor(),
                    snapshot.getRatingAverage(),
                    snapshot.getRatingCount(),
                    snapshot.getInlineReviews().size(),
                    snapshot.isPartial(),
                    row.sourceUrl(),
                    row.createdAt(),
                    row.uploadedAt(),
                    row.uploadTarget()
            ));
        }
        return new SnapshotList(summaries, properties.getRemoteBaseUrl());
    }

    public UploadResult uploadSnapshots(List<UUID> snapshotIds, @Nullable String targetBaseUrl, @Nullable String authToken) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return new UploadResult(List.of(), resolveTargetBaseUrl(targetBaseUrl));
        }
        String baseUrl = resolveTargetBaseUrl(targetBaseUrl);
        RestClient client = RestClient.builder().baseUrl(baseUrl).build();

        List<SnapshotRow> rows = snapshotRepository.findByIds(snapshotIds);
        List<UploadItemResult> results = new ArrayList<>(snapshotIds.size());
        var rowMap = rows.stream().collect(Collectors.toMap(SnapshotRow::snapshotId, r -> r));

        for (UUID snapshotId : snapshotIds) {
            SnapshotRow row = rowMap.get(snapshotId);
            if (row == null) {
                results.add(new UploadItemResult(snapshotId, false, "Snapshot not found"));
                continue;
            }
            if (!StringUtils.hasText(row.sourceHtml())) {
                results.add(new UploadItemResult(row.snapshotId(), false, "No source HTML stored for snapshot"));
                continue;
            }
            try {
                RestClient.RequestBodySpec request = client.post()
                        .uri("/api/admin/products/intake/html")
                        .contentType(MediaType.APPLICATION_JSON);
                if (StringUtils.hasText(authToken)) {
                    request = request.header("Authorization", "Bearer " + authToken.trim());
                } else if (StringUtils.hasText(properties.getRemoteAuthToken())) {
                    request = request.header("Authorization", "Bearer " + properties.getRemoteAuthToken().trim());
                }
                request
                        .body(new UploadPayload(row.sourceHtml(), row.sourceUrl()))
                        .retrieve()
                        .toBodilessEntity();
                snapshotRepository.markUploaded(row.snapshotId(), Instant.now(clock), baseUrl);
                results.add(new UploadItemResult(row.snapshotId(), true, "OK"));
            } catch (RestClientResponseException httpEx) {
                results.add(new UploadItemResult(snapshotId, false,
                        "HTTP " + httpEx.getStatusCode().value() + " " + httpEx.getResponseBodyAsString()));
            } catch (Exception ex) {
                results.add(new UploadItemResult(snapshotId, false, ex.getMessage()));
            }
        }

        return new UploadResult(results, baseUrl);
    }

    private boolean looksBlocked(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("captcha")
                || lower.contains("robot check")
                || lower.contains("enter the characters")
                || lower.contains("signin");
    }

    private String extractUrl(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String candidate = part.trim();
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return trimmed;
    }

    private String resolveTargetBaseUrl(@Nullable String override) {
        String candidate = StringUtils.hasText(override) ? override : properties.getRemoteBaseUrl();
        if (!StringUtils.hasText(candidate)) {
            throw new IllegalStateException("Remote base URL is not configured");
        }
        return candidate.trim();
    }

    public record CsvIngestResult(int processed,
                                  int succeeded,
                                  List<ItemResult> items,
                                  List<Failure> failures,
                                  Path csvPath) {
    }

    public record ItemResult(UUID productId, String asin, boolean created) { }

    public record Failure(String url, String reason) { }

    public record SnapshotList(List<SnapshotSummary> snapshots, String defaultRemoteBaseUrl) { }

    public record SnapshotSummary(UUID snapshotId,
                                  String asin,
                                  String title,
                                  Long priceMinor,
                                  Double ratingAverage,
                                  Long ratingCount,
                                  int inlineReviewCount,
                                  boolean partial,
                                  String sourceUrl,
                                  Instant createdAt,
                                  Instant uploadedAt,
                                  String uploadTarget) { }

    public record UploadResult(List<UploadItemResult> items, String targetBaseUrl) { }

    public record UploadItemResult(UUID snapshotId, boolean success, String message) { }

    private record UploadPayload(String html, String sourceUrl) { }
}
