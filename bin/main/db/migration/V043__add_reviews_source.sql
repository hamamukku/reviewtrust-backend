-- V043__add_reviews_source.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS source text;
