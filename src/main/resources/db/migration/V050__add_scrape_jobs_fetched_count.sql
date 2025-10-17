-- V050__add_scrape_jobs_fetched_count.sql
ALTER TABLE IF EXISTS public.scrape_jobs
  ADD COLUMN IF NOT EXISTS fetched_count integer;
