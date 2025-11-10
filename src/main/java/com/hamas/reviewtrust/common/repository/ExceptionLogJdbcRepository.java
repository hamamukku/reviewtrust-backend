package com.hamas.reviewtrust.common.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository("exceptionLogJdbcRepository") // ★ Bean 名を変更
public class ExceptionLogJdbcRepository {
    private final JdbcTemplate jdbc;

    public ExceptionLogJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(UUID jobId, String scope, String errorCode, String message, String stack) {
        jdbc.update("""
            INSERT INTO exception_logs (id, job_id, scope, error_code, message, stack, created_at)
            VALUES (? , ? , ?, ?, ?, ?, now())
        """, UUID.randomUUID(), jobId, scope, errorCode, message, stack);
    }

    public void saveNoJob(String scope, String errorCode, String message, String stack) {
        jdbc.update("""
            INSERT INTO exception_logs (id, job_id, scope, error_code, message, stack, created_at)
            VALUES (? , NULL, ?, ?, ?, ?, now())
        """, UUID.randomUUID(), scope, errorCode, message, stack);
    }
}
