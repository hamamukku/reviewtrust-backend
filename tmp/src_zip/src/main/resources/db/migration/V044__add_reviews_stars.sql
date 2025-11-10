-- V044__add_reviews_stars.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS stars integer;
