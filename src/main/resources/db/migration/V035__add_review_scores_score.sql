-- V035__add_review_scores_score.sql
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS score integer;
