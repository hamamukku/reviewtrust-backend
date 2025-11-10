-- V049__add_reviews_verified_purchase.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS verified_purchase boolean NOT NULL DEFAULT false;
