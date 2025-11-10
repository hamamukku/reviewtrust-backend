// src/main/java/com/hamas/reviewtrust/domain/reviews/ReviewUpsertRepository.java
package com.hamas.reviewtrust.domain.reviews;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * reviews への安全UPSERT専用リポジトリ。
 * ※ Flyway 側で以下の UNIQUE 制約が作られている前提：
 *   - reviews_ux_product_source_external (product_id, source, external_review_id)
 *   - reviews_ux_product_source_fingerprint (product_id, source, fingerprint)
 */
@Repository
public class ReviewUpsertRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewUpsertRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID upsert(ReviewUpsertRequest r) {
        if (r.externalReviewId() != null && !r.externalReviewId().isBlank()) {
            return upsertByExternalId(r);
        }
        if (r.fingerprint() == null || r.fingerprint().isBlank()) {
            throw new IllegalArgumentException("fingerprint is required when external_review_id is null/blank");
        }
        return upsertByFingerprint(r);
    }

    private UUID upsertByExternalId(ReviewUpsertRequest r) {
        var sql = """
            INSERT INTO public.reviews
              (product_id, source, external_review_id, fingerprint, title, body, rating, review_date, reviewer, review_url, helpful_votes, created_at, updated_at)
            VALUES
              (:productId, :source, :extId, :fingerprint, :title, :body, :rating, :reviewDate, :reviewer, :reviewUrl, :helpfulVotes, now(), now())
            ON CONFLICT ON CONSTRAINT reviews_ux_product_source_external
            DO UPDATE SET
              fingerprint    = COALESCE(EXCLUDED.fingerprint, public.reviews.fingerprint),
              title          = COALESCE(EXCLUDED.title, public.reviews.title),
              body           = COALESCE(EXCLUDED.body, public.reviews.body),
              rating         = COALESCE(EXCLUDED.rating, public.reviews.rating),
              review_date    = COALESCE(EXCLUDED.review_date, public.reviews.review_date),
              reviewer       = COALESCE(EXCLUDED.reviewer, public.reviews.reviewer),
              review_url     = COALESCE(EXCLUDED.review_url, public.reviews.review_url),
              helpful_votes  = COALESCE(EXCLUDED.helpful_votes, public.reviews.helpful_votes),
              updated_at     = now()
            RETURNING id;
            """;
        var params = baseParams(r).addValue("extId", r.externalReviewId());
        return jdbc.queryForObject(sql, params, (rs, i) -> (UUID) rs.getObject("id"));
    }

    private UUID upsertByFingerprint(ReviewUpsertRequest r) {
        var sql = """
            INSERT INTO public.reviews
              (product_id, source, external_review_id, fingerprint, title, body, rating, review_date, reviewer, review_url, helpful_votes, created_at, updated_at)
            VALUES
              (:productId, :source, NULL, :fingerprint, :title, :body, :rating, :reviewDate, :reviewer, :reviewUrl, :helpfulVotes, now(), now())
            ON CONFLICT ON CONSTRAINT reviews_ux_product_source_fingerprint
            DO UPDATE SET
              title          = COALESCE(EXCLUDED.title, public.reviews.title),
              body           = COALESCE(EXCLUDED.body, public.reviews.body),
              rating         = COALESCE(EXCLUDED.rating, public.reviews.rating),
              review_date    = COALESCE(EXCLUDED.review_date, public.reviews.review_date),
              reviewer       = COALESCE(EXCLUDED.reviewer, public.reviews.reviewer),
              review_url     = COALESCE(EXCLUDED.review_url, public.reviews.review_url),
              helpful_votes  = COALESCE(EXCLUDED.helpful_votes, public.reviews.helpful_votes),
              updated_at     = now()
            RETURNING id;
            """;
        var params = baseParams(r);
        return jdbc.queryForObject(sql, params, (rs, i) -> (UUID) rs.getObject("id"));
    }

    private MapSqlParameterSource baseParams(ReviewUpsertRequest r) {
        return new MapSqlParameterSource()
                .addValue("productId", r.productId())
                .addValue("source", r.source())
                .addValue("fingerprint", r.fingerprint())
                .addValue("title", nullIfBlank(r.title()))
                .addValue("body", nullIfBlank(r.body()))
                .addValue("rating", r.rating() == null ? null : Math.max(0, Math.min(5, r.rating())))
                .addValue("reviewDate", r.reviewDate())
                .addValue("reviewer", nullIfBlank(r.reviewer()))
                .addValue("reviewUrl", nullIfBlank(r.reviewUrl()))
                .addValue("helpfulVotes", r.helpfulVotes() == null ? 0 : r.helpfulVotes());
    }

    private static String nullIfBlank(String s){ return (s == null || s.isBlank()) ? null : s; }

    public record ReviewUpsertRequest(
            Object productId,
            String source,
            String externalReviewId,
            String fingerprint,
            String title,
            String body,
            Integer rating,
            LocalDate reviewDate,
            String reviewer,
            String reviewUrl,
            Integer helpfulVotes
    ) {}
}
