-- V038__add_reviews_has_image.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS has_image boolean NOT NULL DEFAULT false;
