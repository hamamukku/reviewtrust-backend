package com.hamas.reviewtrust.domain.reviews.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.config.IntakeProperties;
import com.hamas.reviewtrust.domain.products.entity.Product;
import com.hamas.reviewtrust.domain.products.repo.ProductRepository;
import com.hamas.reviewtrust.domain.reviews.ReviewUpsertRepository;
import com.hamas.reviewtrust.domain.reviews.ReviewUpsertRepository.ReviewUpsertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ReviewIntakeService {

    private static final Logger log = LoggerFactory.getLogger(ReviewIntakeService.class);

    private final IntakeProperties intakeProperties;
    private final ObjectMapper objectMapper;
    private final ReviewUpsertRepository reviewUpsertRepository;
    private final ProductRepository productRepository;

    public ReviewIntakeService(IntakeProperties intakeProperties,
                               ObjectMapper objectMapper,
                               ReviewUpsertRepository reviewUpsertRepository,
                               ProductRepository productRepository) {
        this.intakeProperties = intakeProperties;
        this.objectMapper = objectMapper;
        this.reviewUpsertRepository = reviewUpsertRepository;
        this.productRepository = productRepository;
    }

    public IntakeResult ingestAll(String asinFilter) {
        List<Path> files = getCandidateFiles(asinFilter);
        List<FileResult> results = new ArrayList<>(files.size());
        int totalSucceeded = 0;
        int totalSkipped = 0;
        int totalHistogram = 0;

        for (Path file : files) {
            FileResult result = processFile(file);
            results.add(result);
            totalSucceeded += result.succeeded();
            totalSkipped += result.skipped();
            totalHistogram += result.histogramLines();
        }
        return new IntakeResult(results, results.size(), totalSucceeded, totalSkipped, totalHistogram);
    }

    public List<Path> getCandidateFiles(String asinFilter) {
        String normalizedAsin = normalizeAsin(asinFilter);
        Set<Path> collected = new LinkedHashSet<>();
        for (String dir : intakeProperties.dirList()) {
            if (!StringUtils.hasText(dir)) {
                continue;
            }
            Path base = Path.of(dir).toAbsolutePath().normalize();
            if (!Files.exists(base)) {
                continue;
            }
            try (var stream = Files.walk(base)) {
                stream.filter(Files::isRegularFile)
                        .map(Path::toAbsolutePath)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ndjson"))
                        .filter(path -> matchesAsin(path, normalizedAsin))
                        .forEach(collected::add);
            } catch (Exception e) {
                log.warn("[intake] failed to scan dir={} cause={}", base, e.toString());
            }
        }
        return collected.stream()
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<FileMetadata> describeCandidateFiles(String asinFilter) {
        return getCandidateFiles(asinFilter).stream()
                .map(path -> new FileMetadata(
                        path.toString(),
                        path.getFileName() != null ? path.getFileName().toString() : path.toString(),
                        safeSize(path),
                        safeLastModified(path)
                ))
                .toList();
    }

    private FileResult processFile(Path file) {
        int totalLines = 0;
        int histogramLines = 0;
        int attempted = 0;
        int succeeded = 0;
        int skipped = 0;
        String error = null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                totalLines++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    skipped++;
                    continue;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(trimmed);
                } catch (Exception parseEx) {
                    log.warn("[intake] skip invalid JSON {}:{} cause={}",
                            file.getFileName(), lineNo, parseEx.getMessage());
                    skipped++;
                    continue;
                }

                if (isHistogram(node)) {
                    histogramLines++;
                    continue;
                }

                attempted++;
                ReviewUpsertRequest request = toRequest(node, file, lineNo);
                if (request == null) {
                    skipped++;
                    continue;
                }

                try {
                    reviewUpsertRepository.upsert(request);
                    succeeded++;
                } catch (Exception upsertEx) {
                    log.warn("[intake] upsert failed {}:{} reviewId={} cause={}",
                            file.getFileName(), lineNo, request.externalReviewId(), upsertEx.toString());
                    skipped++;
                }
            }
            log.info("[intake] file={} succeeded={} skipped={} attempted={} histogram={} lines={}",
                    file.getFileName(), succeeded, skipped, attempted, histogramLines, totalLines);
        } catch (Exception e) {
            error = e.toString();
            log.warn("[intake] skip file={} cause={}", file, e.toString(), e);
        }

        long size = safeSize(file);
        Instant lastModified = safeLastModified(file);
        return new FileResult(
                file.toString(),
                file.getFileName() != null ? file.getFileName().toString() : file.toString(),
                size,
                lastModified,
                totalLines,
                histogramLines,
                attempted,
                succeeded,
                skipped,
                error
        );
    }

    private ReviewUpsertRequest toRequest(JsonNode node, Path file, int lineNo) {
        String asin = optText(node, "asin");
        String reviewId = optText(node, "reviewId");
        Double ratingValue = optDouble(node, "rating");
        String url = optText(node, "url");

        if (asin == null || reviewId == null || ratingValue == null || url == null) {
            log.warn("[intake] skip missing fields {}:{} asin={} reviewId={} rating={}",
                    file.getFileName(), lineNo, asin, reviewId, ratingValue);
            return null;
        }

        asin = asin.trim().toUpperCase(Locale.ROOT);
        Optional<Product> productOptional = productRepository.findByAsin(asin);
        if (productOptional.isEmpty()) {
            log.warn("[intake] skip unknown asin {}:{} asin={}", file.getFileName(), lineNo, asin);
            return null;
        }

        int rating = (int) Math.round(ratingValue);
        rating = Math.max(0, Math.min(5, rating));

        String title = optText(node, "title");
        String body = optText(node, "body");
        String author = optText(node, "author");
        Integer helpfulVotes = optInteger(node, "helpfulVotes");
        LocalDate reviewDate = null;

        Product product = productOptional.get();
        return new ReviewUpsertRequest(
                product.getId(),
                "AMAZON",
                reviewId,
                null,
                title,
                body,
                rating,
                reviewDate,
                author,
                reviewId,
                url,
                helpfulVotes
        );
    }

    private static boolean isHistogram(JsonNode node) {
        if (node == null) {
            return false;
        }
        String type = node.path("type").asText();
        return type != null && "histogram".equalsIgnoreCase(type);
    }

    private static String optText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static Double optDouble(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        double d = value.asDouble();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        return d;
    }

    private static Integer optInteger(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.intValue();
        }
        if (value.isNumber()) {
            return (int) Math.round(value.asDouble());
        }
        try {
            String text = value.asText();
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return Integer.parseInt(text.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String normalizeAsin(String asinFilter) {
        if (!StringUtils.hasText(asinFilter)) {
            return null;
        }
        return asinFilter.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean matchesAsin(Path path, String asin) {
        if (asin == null) {
            return true;
        }
        String fileName = path.getFileName() != null
                ? path.getFileName().toString().toUpperCase(Locale.ROOT)
                : "";
        if (fileName.contains(asin)) {
            return true;
        }
        for (Path current = path.getParent(); current != null; current = current.getParent()) {
            Path name = current.getFileName();
            if (name != null && asin.equalsIgnoreCase(name.toString())) {
                return true;
            }
        }
        return false;
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }

    private static Instant safeLastModified(Path path) {
        try {
            FileTime time = Files.getLastModifiedTime(path);
            return time.toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    public record IntakeResult(List<FileResult> files,
                               int totalFiles,
                               int totalSucceeded,
                               int totalSkipped,
                               int totalHistogram) {
    }

    public record FileResult(String path,
                             String fileName,
                             long sizeBytes,
                             Instant lastModified,
                             int totalLines,
                             int histogramLines,
                             int attempted,
                             int succeeded,
                             int skipped,
                             String error) {
        public boolean failed() {
            return error != null;
        }
    }

    public record FileMetadata(String path,
                               String fileName,
                               long sizeBytes,
                               Instant lastModified) {
    }
}
