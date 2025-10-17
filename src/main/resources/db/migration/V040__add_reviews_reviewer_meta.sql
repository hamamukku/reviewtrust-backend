-- V040__add_reviews_reviewer_meta.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS reviewer_meta jsonb NOT NULL DEFAULT '{}'::jsonb;
