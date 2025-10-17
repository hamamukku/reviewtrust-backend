-- V045__add_reviews_status.sql
-- エンティティが EnumType.STRING の想定。まずは text/NULL 可で追加。
ALTER TABLE IF EXISTS public.reviews
  ADD COLUMN IF NOT EXISTS status text;
