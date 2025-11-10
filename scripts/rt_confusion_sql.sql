WITH predictions AS (
    SELECT
        p.name AS label,
        rs.sakura_judge,
        rs.rank,
        rs.product_id
    FROM review_scores rs
    JOIN products p ON p.id = rs.product_id
    WHERE rs.source = 'AMAZON'
)
SELECT label, sakura_judge, COUNT(*) AS count
FROM predictions
GROUP BY label, sakura_judge
ORDER BY label, sakura_judge;

-- overall accuracy by sakura judge
SELECT
    SUM(CASE WHEN label = sakura_judge THEN 1 ELSE 0 END)::decimal / NULLIF(COUNT(*), 0) AS accuracy
FROM predictions;
