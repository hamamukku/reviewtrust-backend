-- V030__add_review_scores_created_at.sql
-- review_scores に作成時刻を追加（Hibernateの検証対応）
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
