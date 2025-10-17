-- V028__add_review_images_path.sql
-- review_images.path を追加（エンティティが文字列を期待しているため text 型で追加）
ALTER TABLE IF EXISTS public.review_images
  ADD COLUMN IF NOT EXISTS path text;

-- 必要なら、後続マイグレーションで NOT NULL 化やデフォルト値の設定を行ってください。
