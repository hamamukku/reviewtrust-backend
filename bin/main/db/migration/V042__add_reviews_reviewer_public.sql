-- V042__add_reviews_reviewer_public.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS reviewer_public boolean NOT NULL DEFAULT false;
