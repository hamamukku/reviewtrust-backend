-- V11__add_error_code_to_exception_logs.sql
-- Hibernateが期待する error_code を追加する（安全に既存データは残す）

ALTER TABLE public.exception_logs
  ADD COLUMN IF NOT EXISTS error_code varchar(64);

CREATE INDEX IF NOT EXISTS ix_exception_logs_error_code ON public.exception_logs (error_code);
