-- Align scrape_jobs schema with new status/target tracking columns
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'state'
  ) THEN
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns
       WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'status'
    ) THEN
      ALTER TABLE public.scrape_jobs RENAME COLUMN state TO status;
    ELSE
      ALTER TABLE public.scrape_jobs DROP COLUMN state;
    END IF;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'target_count'
  ) THEN
    ALTER TABLE public.scrape_jobs RENAME COLUMN target_count TO target_total;
  END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'fetched_count'
  ) THEN
    ALTER TABLE public.scrape_jobs RENAME COLUMN fetched_count TO collected;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'target_total'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN target_total integer DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'collected'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN collected integer DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'upserted'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN upserted integer DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'requested_url'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN requested_url text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'started_at'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN started_at timestamptz;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'finished_at'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN finished_at timestamptz;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'status'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN status text NOT NULL DEFAULT 'QUEUED';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'source'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN source text NOT NULL DEFAULT 'amazon';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'scrape_jobs' AND column_name = 'requested_by'
  ) THEN
    ALTER TABLE public.scrape_jobs ADD COLUMN requested_by text;
  END IF;
END$$;

UPDATE public.scrape_jobs
   SET status = upper(status)
 WHERE status IS NOT NULL;

UPDATE public.scrape_jobs
   SET source = COALESCE(NULLIF(source, ''), 'amazon');

-- Ensure reviews partial uniqueness on external_review_id / fingerprint
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM pg_constraint
     WHERE conrelid = 'public.reviews'::regclass
       AND conname = 'reviews_ux_product_source_external'
  ) THEN
    ALTER TABLE public.reviews DROP CONSTRAINT reviews_ux_product_source_external;
  END IF;
  IF NOT EXISTS (
    SELECT 1
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relname = 'reviews_ux_product_source_external_idx'
       AND n.nspname = 'public'
  ) THEN
    CREATE UNIQUE INDEX reviews_ux_product_source_external_idx
        ON public.reviews (product_id, source, external_review_id)
        WHERE external_review_id IS NOT NULL;
  END IF;

  IF EXISTS (
    SELECT 1
      FROM pg_constraint
     WHERE conrelid = 'public.reviews'::regclass
       AND conname = 'reviews_ux_product_source_fingerprint'
  ) THEN
    ALTER TABLE public.reviews DROP CONSTRAINT reviews_ux_product_source_fingerprint;
  END IF;
  IF NOT EXISTS (
    SELECT 1
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.relname = 'reviews_ux_product_source_fingerprint_idx'
       AND n.nspname = 'public'
  ) THEN
    CREATE UNIQUE INDEX reviews_ux_product_source_fingerprint_idx
        ON public.reviews (product_id, source, fingerprint)
        WHERE fingerprint IS NOT NULL;
  END IF;
END$$;

-- Rebuild review_scores as product/source snapshot table
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_schema='public' AND table_name='review_scores'
                AND column_name='id') THEN
    ALTER TABLE public.review_scores RENAME TO review_scores_legacy;
  END IF;
END$$;

CREATE TABLE IF NOT EXISTS public.review_scores (
  product_id    uuid        NOT NULL,
  source        text        NOT NULL,
  score         integer     NOT NULL,
  rank          char(1)     NOT NULL,
  sakura_judge  text        NOT NULL,
  flags         jsonb       NOT NULL DEFAULT '[]'::jsonb,
  rules         jsonb       NOT NULL DEFAULT '[]'::jsonb,
  metrics       jsonb       NOT NULL DEFAULT '{}'::jsonb,
  computed_at   timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (product_id, source)
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='sakura_judge'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN sakura_judge text NOT NULL DEFAULT 'UNKNOWN';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
     WHERE table_schema='public' AND table_name='review_scores'
       AND constraint_type='PRIMARY KEY'
  ) THEN
    ALTER TABLE public.review_scores
      ADD CONSTRAINT review_scores_pk PRIMARY KEY (product_id, source);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='flags'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN flags jsonb NOT NULL DEFAULT '[]'::jsonb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='rules'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN rules jsonb NOT NULL DEFAULT '[]'::jsonb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='metrics'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN metrics jsonb NOT NULL DEFAULT '{}'::jsonb;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='computed_at'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN computed_at timestamptz NOT NULL DEFAULT now();
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_schema='public' AND table_name='review_scores' AND column_name='updated_at'
  ) THEN
    ALTER TABLE public.review_scores ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
  END IF;
END$$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
     WHERE table_schema='public' AND table_name='review_scores_legacy'
  ) THEN
    INSERT INTO public.review_scores (product_id, source, score, rank, sakura_judge, flags, rules, metrics, computed_at, updated_at)
    SELECT
      COALESCE(rs.product_id, rev.product_id),
      COALESCE(NULLIF(rs.scope, ''), 'AMAZON'),
      COALESCE(rs.score, 0),
      COALESCE(rs.rank, 'C'),
      'UNKNOWN',
      COALESCE(rs.flags, '[]'::jsonb),
      COALESCE(rs.rules, '[]'::jsonb),
      COALESCE(rs.breakdown, '{}'::jsonb),
      COALESCE(rs.created_at, now()),
      now()
    FROM public.review_scores_legacy rs
    LEFT JOIN public.reviews rev ON rev.id = rs.review_id
    ON CONFLICT (product_id, source) DO NOTHING;

    DROP TABLE public.review_scores_legacy;
  END IF;
END$$;

UPDATE public.review_scores
   SET sakura_judge = 'UNKNOWN'
 WHERE sakura_judge IS NULL;

-- re-use updated_at trigger if available
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
     WHERE tgname = 'review_scores_set_updated_at'
  ) THEN
    CREATE TRIGGER review_scores_set_updated_at
      BEFORE UPDATE ON public.review_scores
      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
  END IF;
END$$;
