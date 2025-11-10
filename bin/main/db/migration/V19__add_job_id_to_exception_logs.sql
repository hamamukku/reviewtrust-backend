-- V19__add_job_id_to_exception_logs.sql
ALTER TABLE public.exception_logs
  ADD COLUMN IF NOT EXISTS job_id uuid;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_exception_logs_job_id' AND n.nspname = 'public'
  ) THEN
    CREATE INDEX ix_exception_logs_job_id ON public.exception_logs (job_id);
  END IF;
END$$;
