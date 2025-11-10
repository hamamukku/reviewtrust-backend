-- Export feature set per product (requires run via psql \copy etc.)
SELECT
    p.id AS product_id,
    p.name AS label,
    p.url,
    rs.score,
    rs.rank,
    rs.sakura_judge,
    (rs.metrics ->> 'total_reviews')::int       AS total_reviews,
    (rs.metrics ->> 'dist_bias')::numeric       AS dist_bias,
    (rs.metrics ->> 'duplicate_rate')::numeric  AS duplicate_rate,
    (rs.metrics ->> 'surge_ratio')::numeric     AS surge_ratio,
    (rs.metrics ->> 'noise_ratio')::numeric     AS noise_ratio,
    (rs.metrics ->> 'recent_reviews')::int      AS recent_reviews,
    (rs.metrics ->> 'window_days')::int         AS window_days,
    rs.updated_at
FROM review_scores rs
JOIN products p ON p.id = rs.product_id
WHERE rs.source = 'AMAZON'
ORDER BY p.id;
