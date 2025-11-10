-- products の公開状態と可視性
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='products' AND column_name='visible'
  ) THEN
    ALTER TABLE public.products ADD COLUMN visible boolean NOT NULL DEFAULT true;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='products' AND column_name='publish_status'
  ) THEN
    ALTER TABLE public.products ADD COLUMN publish_status text NOT NULL DEFAULT 'PUBLISHED'
      CHECK (publish_status IN ('DRAFT','APPROVED','PUBLISHED','ARCHIVED'));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='products' AND column_name='published_at'
  ) THEN
    ALTER TABLE public.products ADD COLUMN published_at timestamptz;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='products' AND column_name='hidden_at'
  ) THEN
    ALTER TABLE public.products ADD COLUMN hidden_at timestamptz;
  END IF;
END$$;

-- 既存データを後方互換で PUBLISHED に寄せる（必要なら変更）
UPDATE public.products
   SET publish_status='PUBLISHED'
 WHERE publish_status IS NULL;

-- reviews のステータスと可視性（既存 reviews を拡張）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='status'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN status text NOT NULL DEFAULT 'DRAFT'
      CHECK (status IN ('DRAFT','APPROVED','PUBLISHED','REJECTED'));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='visible'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN visible boolean NOT NULL DEFAULT true;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='moderated_by'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN moderated_by text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='moderated_reason'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN moderated_reason text;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='reviews' AND column_name='moderated_at'
  ) THEN
    ALTER TABLE public.reviews ADD COLUMN moderated_at timestamptz;
  END IF;
END$$;

-- よく使うインデックス
CREATE INDEX IF NOT EXISTS idx_products_visibility ON public.products(visible, publish_status);
CREATE INDEX IF NOT EXISTS idx_reviews_product_status ON public.reviews(product_id, status, visible);
