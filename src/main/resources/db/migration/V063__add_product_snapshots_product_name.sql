-- Ensure product snapshots store the normalized product name captured during scraping
ALTER TABLE IF EXISTS public.product_snapshots
    ADD COLUMN IF NOT EXISTS product_name text;

-- Backfill missing values from snapshot JSON (preferred) or the products table
UPDATE public.product_snapshots ps
SET product_name = COALESCE(
        NULLIF(ps.snapshot_json ->> 'title', ''),
        p.title
    )
FROM public.products p
WHERE p.id = ps.product_id
  AND (ps.product_name IS NULL OR ps.product_name = '');
