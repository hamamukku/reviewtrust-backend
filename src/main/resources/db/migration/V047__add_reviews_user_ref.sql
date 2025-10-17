-- V047__add_reviews_user_ref.sql
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS user_ref text;
