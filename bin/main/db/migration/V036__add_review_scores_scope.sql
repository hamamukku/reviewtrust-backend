-- V036__add_review_scores_scope.sql
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS scope text;
