package com.hamas.reviewtrust.domain.products.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hamas.reviewtrust.domain.scraping.model.ProductPageSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProductSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProductSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SnapshotRow> findRecent(int limit, boolean includeUploaded) {
        String sql = """
                select ps.id,
                       ps.product_id,
                       p.asin,
                       p.title,
                       ps.product_name as snapshot_product_name,
                       ps.source_url,
                       ps.source_html,
                       ps.snapshot_json,
                       ps.created_at,
                       ps.uploaded_at,
                       ps.upload_target
                from product_snapshots ps
                join products p on p.id = ps.product_id
                %s
                order by ps.created_at desc
                limit ?
                """.formatted(includeUploaded ? "" : "where ps.uploaded_at is null");

        return jdbcTemplate.query(sql, new SnapshotRowMapper(), limit);
    }

    public List<SnapshotRow> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String inSql = ids.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("?");
        String sql = """
                select ps.id,
                       ps.product_id,
                       p.asin,
                       p.title,
                       ps.product_name as snapshot_product_name,
                       ps.source_url,
                       ps.source_html,
                       ps.snapshot_json,
                       ps.created_at,
                       ps.uploaded_at,
                       ps.upload_target
                from product_snapshots ps
                join products p on p.id = ps.product_id
                where ps.id in (%s)
                """.formatted(inSql);
        return jdbcTemplate.query(sql, new SnapshotRowMapper(), ids.toArray());
    }

    public Optional<SnapshotRow> findLatestByAsin(String asin) {
        if (!StringUtils.hasText(asin)) {
            return Optional.empty();
        }
        String sql = """
                select ps.id,
                       ps.product_id,
                       p.asin,
                       p.title,
                       ps.product_name as snapshot_product_name,
                       ps.source_url,
                       ps.source_html,
                       ps.snapshot_json,
                       ps.created_at,
                       ps.uploaded_at,
                       ps.upload_target
                from product_snapshots ps
                join products p on p.id = ps.product_id
                where upper(p.asin) = ?
                order by ps.created_at desc
                limit 1
                """;
        String normalized = asin.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query(sql, new SnapshotRowMapper(), normalized)
                .stream()
                .findFirst();
    }

    public void markUploaded(UUID id, Instant uploadedAt, String target) {
        jdbcTemplate.update(
                "update product_snapshots set uploaded_at = ?, upload_target = ? where id = ?",
                uploadedAt,
                StringUtils.hasText(target) ? target : null,
                id
        );
    }

    private class SnapshotRowMapper implements RowMapper<SnapshotRow> {
        @Override
        public SnapshotRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            UUID id = rs.getObject("id", UUID.class);
            UUID productId = rs.getObject("product_id", UUID.class);
            String asin = rs.getString("asin");
            String title = rs.getString("title");
            String productName = rs.getString("snapshot_product_name");
            String sourceUrl = rs.getString("source_url");
            String sourceHtml = rs.getString("source_html");
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            Instant uploadedAt = rs.getTimestamp("uploaded_at") != null
                    ? rs.getTimestamp("uploaded_at").toInstant()
                    : null;
            String uploadTarget = rs.getString("upload_target");

            ProductPageSnapshot snapshot;
            try {
                snapshot = objectMapper.readValue(rs.getString("snapshot_json"), ProductPageSnapshot.class);
            } catch (Exception e) {
                snapshot = ProductPageSnapshot.builder()
                        .asin(asin)
                        .title(StringUtils.hasText(productName) ? productName : title)
                        .partial(true)
                        .build();
            }

            return new SnapshotRow(id, productId, asin, title, productName, sourceUrl, sourceHtml, snapshot, createdAt, uploadedAt, uploadTarget);
        }
    }

    public record SnapshotRow(UUID snapshotId,
                              UUID productId,
                              String asin,
                              String title,
                              String productName,
                              String sourceUrl,
                              String sourceHtml,
                              ProductPageSnapshot snapshot,
                              Instant createdAt,
                              Instant uploadedAt,
                              String uploadTarget) {
    }
}
