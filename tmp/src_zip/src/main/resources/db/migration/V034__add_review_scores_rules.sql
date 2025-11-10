-- V034__add_review_scores_rules.sql
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS rules jsonb NOT NULL DEFAULT '{}'::jsonb;
