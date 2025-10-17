-- V027__review_images_bytes_migration.sql
-- 1) 既存のバイナリ列 (bytea) を退避（存在時のみリネーム）
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'review_images'
      AND column_name = 'bytes'
  ) THEN
    EXECUTE 'ALTER TABLE public.review_images RENAME COLUMN bytes TO bytes_blob';
  END IF;
END
$$;

-- 2) Hibernate が期待する型（integer）として新しいカラムを追加
ALTER TABLE IF EXISTS public.review_images ADD COLUMN IF NOT EXISTS bytes integer NOT NULL DEFAULT 0;

-- 注意:
--  - bytes_blob に既存の画像バイナリが残ります（非破壊）。
--  - 後で設計に従って移行（例: S3 へ）・削除してください。
