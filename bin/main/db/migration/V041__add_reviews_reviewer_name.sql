-- V041__add_reviews_reviewer_name.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS reviewer_name text;
