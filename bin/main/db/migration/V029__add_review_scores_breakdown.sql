-- V029__add_review_scores_breakdown.sql
-- review_scores.breakdown を JSONB で追加（ルール寄与などの内訳想定）
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS breakdown jsonb NOT NULL DEFAULT '{}'::jsonb;
