-- V031__add_review_scores_flags.sql
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS flags jsonb NOT NULL DEFAULT '[]'::jsonb;
