-- V21__add_stack_to_exception_logs.sql
-- 安全（idempotent）に exception_logs.stack を追加する

ALTER TABLE public.exception_logs
  ADD COLUMN IF NOT EXISTS stack text;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_exception_logs_stack' AND n.nspname = 'public'
  ) THEN
    CREATE INDEX ix_exception_logs_stack ON public.exception_logs USING gin (to_tsvector('simple', coalesce(stack, '')));
  END IF;
END$$;
