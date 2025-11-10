-- V032__add_review_scores_product_id.sql

-- 1) product_id 列を追加（まずは NULL 可で追加）
ALTER TABLE IF EXISTS public.review_scores
  ADD COLUMN IF NOT EXISTS product_id uuid;

-- 2) 可能なら reviews 経由でバックフィル（review_id -> reviews.product_id）
--    reviews に (id uuid, product_id uuid) が存在する前提。なければこの UPDATE は no-op。
UPDATE public.review_scores rs
SET product_id = r.product_id
FROM public.reviews r
WHERE rs.review_id = r.id
  AND rs.product_id IS NULL;

-- 3) 参照用の軽い index（NOT NULL 化やFKは後続で）
CREATE INDEX IF NOT EXISTS ix_review_scores_product ON public.review_scores(product_id);

-- 4) （任意）外部キーを「遅延 + NOT VALID」で先に貼る。後で VALIDATE 可能。
-- ALTER TABLE public.review_scores
--   ADD CONSTRAINT fk_review_scores_product
--   FOREIGN KEY (product_id) REFERENCES public.products(id)
--   DEFERRABLE INITIALLY DEFERRED NOT VALID;
