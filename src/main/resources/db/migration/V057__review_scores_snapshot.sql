-- 最新スコアのスナップショット（1製品1レコード運用）
CREATE TABLE IF NOT EXISTS public.review_scores (
  product_id     uuid PRIMARY KEY,
  score          integer CHECK (score BETWEEN 0 AND 100),
  rank           char(1) CHECK (rank IN ('A','B','C')),
  sakura_judge   text CHECK (sakura_judge IN ('SAFE','LIKELY_SAKURA','SAKURA')),
  metrics        jsonb,          -- 例: {"dist_bias":0.42,"duplicates":0.31,"surge_z":1.9,"noise":0.12}
  flags          text[],         -- 例: {"ATTN_DUPLICATE","ATTN_SURGE"}
  rules          jsonb,          -- ルール明細配列
  computed_at    timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'review_scores_set_updated_at') THEN
    CREATE TRIGGER review_scores_set_updated_at BEFORE UPDATE ON public.review_scores
      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
  END IF;
END$$;

-- 参照に便利なインデックス
CREATE INDEX IF NOT EXISTS idx_review_scores_computed_at ON public.review_scores(computed_at DESC);
