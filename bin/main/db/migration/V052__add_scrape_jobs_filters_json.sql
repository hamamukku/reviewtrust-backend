-- V052__add_scrape_jobs_filters_json.sql
ALTER TABLE IF EXISTS public.scrape_jobs
  ADD COLUMN IF NOT EXISTS filters_json jsonb NOT NULL DEFAULT '{}'::jsonb;
