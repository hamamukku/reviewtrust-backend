-- V054: reviews の重複抑止（外部ID/指紋）を安全に適用

DO $$
BEGIN
  -- 必要な列を追加（無ければ）
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='external_review_id'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN external_review_id text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='fingerprint'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN fingerprint text;
  END IF;

  -- UNIQUE 制約を追加（無ければ）
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'reviews_ux_product_source_external'
  ) THEN
    ALTER TABLE public.reviews
      ADD CONSTRAINT reviews_ux_product_source_external
      UNIQUE (product_id, source, external_review_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'reviews_ux_product_source_fingerprint'
  ) THEN
    ALTER TABLE public.reviews
      ADD CONSTRAINT reviews_ux_product_source_fingerprint
      UNIQUE (product_id, source, fingerprint);
  END IF;
END$$;
