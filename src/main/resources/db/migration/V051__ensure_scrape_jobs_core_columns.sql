-- V051__ensure_scrape_jobs_core_columns.sql
-- 状態/件数/時刻/参照の“定番”をまとめて保証
ALTER TABLE IF EXISTS public.scrape_jobs
  ADD COLUMN IF NOT EXISTS state text,                 -- 'queued'|'running'|'ok'|'failed' 想定
  ADD COLUMN IF NOT EXISTS target_count integer,
  ADD COLUMN IF NOT EXISTS fetched_count integer,
  ADD COLUMN IF NOT EXISTS started_at   timestamptz,
  ADD COLUMN IF NOT EXISTS finished_at  timestamptz,
  ADD COLUMN IF NOT EXISTS product_id   uuid;

-- 参照・検索用の軽い index（存在すればスキップ）
CREATE INDEX IF NOT EXISTS ix_scrape_jobs_state      ON public.scrape_jobs(state);
CREATE INDEX IF NOT EXISTS ix_scrape_jobs_started_at ON public.scrape_jobs(started_at);
CREATE INDEX IF NOT EXISTS ix_scrape_jobs_product    ON public.scrape_jobs(product_id);
