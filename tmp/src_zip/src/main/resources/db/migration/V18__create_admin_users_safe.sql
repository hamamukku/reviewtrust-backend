-- V18__create_admin_users_safe.sql  (idempotent safe version)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) テーブルが無ければ作る（既存テーブルがあるならスキップ）
CREATE TABLE IF NOT EXISTS public.admin_users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

-- 2) 必要なカラムを足す（既にあれば何もしない）
ALTER TABLE public.admin_users
  ADD COLUMN IF NOT EXISTS username varchar(64),
  ADD COLUMN IF NOT EXISTS email varchar(254),
  ADD COLUMN IF NOT EXISTS password_hash varchar(256),
  ADD COLUMN IF NOT EXISTS roles varchar(256),
  ADD COLUMN IF NOT EXISTS enabled boolean DEFAULT true,
  ADD COLUMN IF NOT EXISTS created_at timestamptz DEFAULT now(),
  ADD COLUMN IF NOT EXISTS updated_at timestamptz DEFAULT now();

-- 3) ユニーク制約／インデックス（存在チェックを組み込む）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_admin_users_username' AND n.nspname = 'public'
  ) THEN
    CREATE INDEX ix_admin_users_username ON public.admin_users (username);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_admin_users_email' AND n.nspname = 'public'
  ) THEN
    CREATE INDEX ix_admin_users_email ON public.admin_users (email);
  END IF;
END$$;
