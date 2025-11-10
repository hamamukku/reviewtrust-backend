-- V10__create_exception_logs.sql
-- 目的: Hibernate が期待する exception_logs テーブルを追加（既存運用を壊さない安全仕様）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.exception_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at timestamptz NOT NULL DEFAULT now(),
  service_name varchar(128),
  level varchar(32),
  message text,
  stacktrace text,
  actor_id uuid,
  meta jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_exception_logs_occurred_at ON public.exception_logs (occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_exception_logs_service_name ON public.exception_logs (service_name);
CREATE INDEX IF NOT EXISTS ix_exception_logs_actor_id ON public.exception_logs (actor_id);
CREATE INDEX IF NOT EXISTS ix_exception_logs_meta ON public.exception_logs USING gin (meta);

