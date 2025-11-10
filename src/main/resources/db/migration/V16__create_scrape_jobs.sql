-- V16__create_scrape_jobs.sql
CREATE TABLE IF NOT EXISTS public.scrape_jobs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid,
  status varchar(32) NOT NULL DEFAULT 'queued', -- queued, running, success, failed
  attempt_count int NOT NULL DEFAULT 0,
  last_error text,
  scheduled_at timestamptz,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_scrape_jobs_product_id ON public.scrape_jobs (product_id);
CREATE INDEX IF NOT EXISTS ix_scrape_jobs_status ON public.scrape_jobs (status);
