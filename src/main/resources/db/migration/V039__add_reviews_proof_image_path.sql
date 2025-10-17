-- V039__add_reviews_proof_image_path.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS proof_image_path text;
