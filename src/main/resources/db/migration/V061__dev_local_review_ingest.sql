-- Ensure reviews table contains the columns required for local dev ingestion
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'source_review_id'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN source_review_id text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'stars'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN stars smallint;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'title'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN title text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'author'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN author text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'body'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN body text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'body_length'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN body_length integer;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'rating_text'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN rating_text text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'posted_at'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN posted_at timestamptz;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'collected_at'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN collected_at timestamptz;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'heuristics'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN heuristics jsonb NOT NULL DEFAULT '{}'::jsonb;
  END IF;
END$$;

-- Maintain default for heuristics when the column already existed without a default
ALTER TABLE public.reviews
  ALTER COLUMN heuristics SET DEFAULT '{}'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS ux_reviews_source_review_id
  ON public.reviews (source, source_review_id)
  WHERE source_review_id IS NOT NULL;

ALTER TABLE public.products
  ADD COLUMN IF NOT EXISTS url text;

CREATE TABLE IF NOT EXISTS public.product_stats (
  product_id        uuid PRIMARY KEY,
  rating_average    numeric(3,2),
  rating_count      integer,
  ratings_histogram jsonb,
  captured_at       timestamptz,
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT fk_product_stats_product
    FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE
);
