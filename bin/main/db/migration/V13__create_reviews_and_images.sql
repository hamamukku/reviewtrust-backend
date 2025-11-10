-- V13__create_reviews_and_images.sql
CREATE TABLE IF NOT EXISTS public.reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid,
  author varchar(256),
  author_id varchar(128),
  rating int,
  title text,
  body text,
  created_at timestamptz,
  scraped_at timestamptz NOT NULL DEFAULT now(),
  fingerprint varchar(128),
  raw jsonb,
  created_by uuid,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_reviews_product_id ON public.reviews (product_id);
CREATE INDEX IF NOT EXISTS ix_reviews_fingerprint ON public.reviews (fingerprint);

CREATE TABLE IF NOT EXISTS public.review_images (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  review_id uuid NOT NULL,
  url text,
  width int,
  height int,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_review_images_review_id ON public.review_images (review_id);
