-- V053__scrape_jobs_filters_json_to_text.sql
ALTER TABLE public.scrape_jobs
  ALTER COLUMN filters_json DROP DEFAULT,
  ALTER COLUMN filters_json TYPE text USING filters_json::text;
