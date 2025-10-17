-- V046__add_reviews_text.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS text text;
