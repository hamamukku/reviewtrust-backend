-- V17__create_exception_logs_extra_cols.sql
ALTER TABLE public.exception_logs
  ADD COLUMN IF NOT EXISTS error_type varchar(128),
  ADD COLUMN IF NOT EXISTS context jsonb;

UPDATE public.exception_logs
SET context = jsonb_build_object('message', message, 'stacktrace', stacktrace)
WHERE context IS NULL;

CREATE INDEX IF NOT EXISTS ix_exception_logs_error_type ON public.exception_logs (error_type);
CREATE INDEX IF NOT EXISTS ix_exception_logs_context ON public.exception_logs USING gin (context);
