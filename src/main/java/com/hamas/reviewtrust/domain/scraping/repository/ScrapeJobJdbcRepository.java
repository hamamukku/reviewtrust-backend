package com.hamas.reviewtrust.domain.scraping.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Low level helper around {@code scrape_jobs}. It keeps the schema details in one place so the
 * service layer can focus on orchestration.
 */
@Repository("scrapeJobJdbcRepository")
public class ScrapeJobJdbcRepository {

    private final JdbcTemplate jdbc;

    public ScrapeJobJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a queued job row for the given product/source pair.
     *
     * @param productId   product identifier
     * @param source      logical scrape source (e.g. {@code AMAZON})
     * @param requestedUrl URL to fetch (may be {@code null} when the scraper derives it)
     * @param targetTotal desired number of reviews that should be collected
     * @param requestedBy optional actor used for audit/idempotency
     * @return generated job id
     */
    public UUID insertQueued(UUID productId, String source, String requestedUrl, int targetTotal, String requestedBy) {
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO scrape_jobs
                  (id, product_id, source, requested_url, status, target_total, collected, upserted,
                   requested_by, created_at, updated_at, started_at, finished_at, message)
                VALUES
                  (?, ?, ?, ?, 'QUEUED', ?, 0, 0, ?, now(), now(), NULL, NULL, NULL)
                """,
                jobId,
                productId,
                normaliseSource(source),
                requestedUrl,
                targetTotal,
                requestedBy
        );
        return jobId;
    }

    public void markRunning(UUID jobId) {
        jdbc.update("""
                UPDATE scrape_jobs
                   SET status='RUNNING', started_at=now(), updated_at=now()
                 WHERE id=?
                """, jobId);
    }

    /**
     * Update the counters while the scraper is still running.
     */
    public void updateProgress(UUID jobId, int collected, int upserted, String message) {
        jdbc.update("""
                UPDATE scrape_jobs
                   SET collected=?, upserted=?, message=?, updated_at=now()
                 WHERE id=?
                """, collected, upserted, message, jobId);
    }

    public void markOk(UUID jobId, int collected, int upserted) {
        jdbc.update("""
                UPDATE scrape_jobs
                   SET status='SUCCEEDED',
                       collected=?,
                       upserted=?,
                       finished_at=now(),
                       updated_at=now()
                 WHERE id=?
                """, collected, upserted, jobId);
    }

    public void markFailed(UUID jobId, String errorCode, String message) {
        String finalMessage = buildFailureMessage(errorCode, message);
        jdbc.update("""
                UPDATE scrape_jobs
                   SET status='FAILED',
                       message=?,
                       last_error=?,
                       finished_at=now(),
                       updated_at=now()
                 WHERE id=?
                """, finalMessage, errorCode, jobId);
    }

    /**
     * Helper for idempotent scripts: touch the timestamps of an already-finished job.
     */
    public void touchFinished(UUID jobId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                UPDATE scrape_jobs
                   SET updated_at=?, finished_at=COALESCE(finished_at, ?)
                 WHERE id=?
                """, now, now, jobId);
    }

    private static String normaliseSource(String source) {
        String value = (source == null || source.isBlank()) ? "amazon" : source.trim();
        return value.toLowerCase();
    }

    private static String buildFailureMessage(String errorCode, String message) {
        if (message == null || message.isBlank()) {
            return Objects.requireNonNullElse(errorCode, "FAILED");
        }
        if (errorCode == null || errorCode.isBlank()) {
            return message;
        }
        return errorCode + ": " + message;
    }
}
