-- V14__create_review_scores.sql
CREATE TABLE IF NOT EXISTS public.review_scores (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  review_id uuid NOT NULL,
  score numeric(5,3) NOT NULL,
  score_label varchar(16),
  reason jsonb,
  computed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_review_scores_review_id ON public.review_scores (review_id);
CREATE INDEX IF NOT EXISTS ix_review_scores_score ON public.review_scores (score);
