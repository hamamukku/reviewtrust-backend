-- V045__add_reviews_status.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS status text;
