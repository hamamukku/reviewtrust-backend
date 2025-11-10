-- V9__align_audit_logs_columns.sql
-- 目的: Hibernate が期待する列名 (meta, target_type, target_id) を追加し、
--       既存カラム(detail, resource_type, resource_id) の値を移行して互換性を確保する。

ALTER TABLE public.audit_logs
  ADD COLUMN IF NOT EXISTS meta jsonb;

UPDATE public.audit_logs
SET meta = detail
WHERE meta IS NULL AND detail IS NOT NULL;

ALTER TABLE public.audit_logs
  ADD COLUMN IF NOT EXISTS target_type varchar(64);

ALTER TABLE public.audit_logs
  ADD COLUMN IF NOT EXISTS target_id uuid;

UPDATE public.audit_logs
SET target_type = resource_type
WHERE target_type IS NULL AND resource_type IS NOT NULL;

UPDATE public.audit_logs
SET target_id = resource_id
WHERE target_id IS NULL AND resource_id IS NOT NULL;

-- インデックス（検索を速くする）
CREATE INDEX IF NOT EXISTS ix_audit_logs_target_type ON public.audit_logs (target_type);
CREATE INDEX IF NOT EXISTS ix_audit_logs_target_id ON public.audit_logs (target_id);
CREATE INDEX IF NOT EXISTS ix_audit_logs_meta ON public.audit_logs USING gin (meta);
