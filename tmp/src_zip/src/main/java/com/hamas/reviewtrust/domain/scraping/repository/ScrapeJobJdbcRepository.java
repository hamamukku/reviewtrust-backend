package com.hamas.reviewtrust.domain.scraping.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository("scrapeJobJdbcRepository") // ← Bean名を明示（衝突回避）
public class ScrapeJobJdbcRepository {
    private final JdbcTemplate jdbc;

    public ScrapeJobJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertQueuedForProduct(UUID productId, int targetCount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO scrape_jobs
              (id, product_id, state, target_count, fetched_count, created_at, started_at, finished_at)
            VALUES
              (?,  ?,         'queued', ?,           0,            now(),     NULL,       NULL)
        """, id, productId, targetCount);
        return id;
    }

    public void markRunning(UUID jobId) {
        jdbc.update("""
            UPDATE scrape_jobs
               SET state='running', started_at=now()
             WHERE id=?
        """, jobId);
    }

    public void markOk(UUID jobId, int fetchedCount) {
        jdbc.update("""
            UPDATE scrape_jobs
               SET state='ok', fetched_count=?, finished_at=now()
             WHERE id=?
        """, fetchedCount, jobId);
    }

    public void markFailed(UUID jobId, String errorCode) {
        // errorCode は exception_logs に保存。ここでは状態のみ。
        jdbc.update("""
            UPDATE scrape_jobs
               SET state='failed', finished_at=now()
             WHERE id=?
        """, jobId);
    }
}
