-- V033__add_review_scores_rank.sql
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS rank char(1);

-- 許容値制約（既に制約があればスキップされるように名前を固定）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_review_scores_rank_abc'
  ) THEN
    ALTER TABLE public.review_scores
      ADD CONSTRAINT chk_review_scores_rank_abc
      CHECK (rank IN ('A','B','C'));
  END IF;
END $$;

-- （任意）検索向けインデックス
CREATE INDEX IF NOT EXISTS ix_review_scores_rank ON public.review_scores(rank);
