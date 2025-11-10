package com.hamas.reviewtrust.domain.reviews;

import com.hamas.reviewtrust.common.hash.TextHash;
import com.hamas.reviewtrust.common.text.TextNormalizer;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Repository that performs idempotent upsert operations into {@code reviews}. It handles
 * fingerprint generation and ensures that the unique constraints (external id / fingerprint)
 * are honoured.
 */
@Repository
public class ReviewUpsertRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ReviewUpsertRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID upsert(ReviewUpsertRequest request) {
        ReviewUpsertRequest normalised = normalise(request);
        if (hasExternalId(normalised)) {
            return upsertByExternalId(normalised);
        }
        return upsertByFingerprint(normalised);
    }

    private UUID upsertByExternalId(ReviewUpsertRequest r) {
        var sql = """
                INSERT INTO public.reviews
                  (product_id, source, external_review_id, fingerprint, title, body, rating,
                   review_date, reviewer, reviewer_ref, review_url, helpful_votes, created_at, updated_at)
                VALUES
                  (:productId, :source, :externalReviewId, :fingerprint, :title, :body, :rating,
                   :reviewDate, :reviewer, :reviewerRef, :reviewUrl, :helpfulVotes, now(), now())
                ON CONFLICT (product_id, source, external_review_id)
                WHERE external_review_id IS NOT NULL
                DO UPDATE SET
                  fingerprint    = COALESCE(EXCLUDED.fingerprint, public.reviews.fingerprint),
                  title          = COALESCE(EXCLUDED.title, public.reviews.title),
                  body           = COALESCE(EXCLUDED.body, public.reviews.body),
                  rating         = COALESCE(EXCLUDED.rating, public.reviews.rating),
                  review_date    = COALESCE(EXCLUDED.review_date, public.reviews.review_date),
                  reviewer       = COALESCE(EXCLUDED.reviewer, public.reviews.reviewer),
                  reviewer_ref   = COALESCE(EXCLUDED.reviewer_ref, public.reviews.reviewer_ref),
                  review_url     = COALESCE(EXCLUDED.review_url, public.reviews.review_url),
                  helpful_votes  = COALESCE(EXCLUDED.helpful_votes, public.reviews.helpful_votes),
                  updated_at     = now()
                RETURNING id;
                """;
        return jdbc.queryForObject(sql, toParams(r), (rs, i) -> (UUID) rs.getObject("id"));
    }

    private UUID upsertByFingerprint(ReviewUpsertRequest r) {
        var sql = """
                INSERT INTO public.reviews
                  (product_id, source, external_review_id, fingerprint, title, body, rating,
                   review_date, reviewer, reviewer_ref, review_url, helpful_votes, created_at, updated_at)
                VALUES
                  (:productId, :source, NULL, :fingerprint, :title, :body, :rating,
                   :reviewDate, :reviewer, :reviewerRef, :reviewUrl, :helpfulVotes, now(), now())
                ON CONFLICT (product_id, source, fingerprint)
                WHERE fingerprint IS NOT NULL
                DO UPDATE SET
                  title          = COALESCE(EXCLUDED.title, public.reviews.title),
                  body           = COALESCE(EXCLUDED.body, public.reviews.body),
                  rating         = COALESCE(EXCLUDED.rating, public.reviews.rating),
                  review_date    = COALESCE(EXCLUDED.review_date, public.reviews.review_date),
                  reviewer       = COALESCE(EXCLUDED.reviewer, public.reviews.reviewer),
                  reviewer_ref   = COALESCE(EXCLUDED.reviewer_ref, public.reviews.reviewer_ref),
                  review_url     = COALESCE(EXCLUDED.review_url, public.reviews.review_url),
                  helpful_votes  = COALESCE(EXCLUDED.helpful_votes, public.reviews.helpful_votes),
                  updated_at     = now()
                RETURNING id;
                """;
        return jdbc.queryForObject(sql, toParams(r), (rs, i) -> (UUID) rs.getObject("id"));
    }

    private MapSqlParameterSource toParams(ReviewUpsertRequest r) {
        return new MapSqlParameterSource()
                .addValue("productId", r.productId())
                .addValue("source", r.source())
                .addValue("externalReviewId", r.externalReviewId())
                .addValue("fingerprint", r.fingerprint())
                .addValue("title", nullIfBlank(r.title()))
                .addValue("body", nullIfBlank(r.body()))
                .addValue("rating", r.rating())
                .addValue("reviewDate", r.reviewDate())
                .addValue("reviewer", nullIfBlank(r.reviewer()))
                .addValue("reviewerRef", nullIfBlank(r.reviewerRef()))
                .addValue("reviewUrl", nullIfBlank(r.reviewUrl()))
                .addValue("helpfulVotes", r.helpfulVotes());
    }

    private static ReviewUpsertRequest normalise(ReviewUpsertRequest r) {
        String source = (r.source() == null || r.source().isBlank())
                ? "AMAZON"
                : r.source().trim().toUpperCase(Locale.ROOT);

        Integer rating = r.rating() == null ? null : Math.max(0, Math.min(5, r.rating()));
        Integer helpful = r.helpfulVotes() == null ? 0 : Math.max(0, r.helpfulVotes());

        String normalisedBody = normaliseText(r.body());
        String normalisedTitle = normaliseText(r.title());
        String reviewerRef = (r.reviewerRef() == null || r.reviewerRef().isBlank())
                ? deriveReviewerRef(r.reviewer(), r.reviewUrl())
                : r.reviewerRef();

        String fingerprint = hasExternalId(r)
                ? r.fingerprint()
                : buildFingerprint(normalisedBody, normalisedTitle, reviewerRef, r.reviewDate());

        return new ReviewUpsertRequest(
                r.productId(),
                source,
                r.externalReviewId(),
                fingerprint,
                normalisedTitle,
                normalisedBody,
                rating,
                r.reviewDate(),
                r.reviewer(),
                reviewerRef,
                r.reviewUrl(),
                helpful
        );
    }

    private static String buildFingerprint(String body, String title, String reviewerRef, LocalDate reviewDate) {
        if (body != null && body.isBlank()) body = null;
        if (title != null && title.isBlank()) title = null;
        if (reviewerRef != null && reviewerRef.isBlank()) reviewerRef = null;

        StringBuilder key = new StringBuilder();
        if (title != null) key.append(title);
        if (body != null) key.append('|').append(body);
        if (reviewerRef != null) key.append('|').append(reviewerRef);
        if (reviewDate != null) key.append('|').append(reviewDate.toEpochDay());

        if (key.length() == 0) {
            throw new IllegalArgumentException("fingerprint requires text or reviewer information");
        }
        return TextHash.sha256Hex(key.toString());
    }

    private static String deriveReviewerRef(String reviewer, String url) {
        if (url != null && !url.isBlank()) {
            return url.trim();
        }
        if (reviewer != null && !reviewer.isBlank()) {
            return reviewer.trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean hasExternalId(ReviewUpsertRequest r) {
        return r.externalReviewId() != null && !r.externalReviewId().isBlank();
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String normaliseText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String norm = TextNormalizer.normalize(value);
        return (norm == null || norm.isBlank()) ? value.trim() : norm;
    }

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
            String reviewerRef,
            String reviewUrl,
            Integer helpfulVotes
    ) {}
}
