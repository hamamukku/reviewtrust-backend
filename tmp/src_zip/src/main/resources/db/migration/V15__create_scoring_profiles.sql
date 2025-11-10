-- V15__create_scoring_profiles.sql
CREATE TABLE IF NOT EXISTS public.scoring_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name varchar(128) NOT NULL,
  description text,
  rules jsonb,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_scoring_profiles_name ON public.scoring_profiles (name);
CREATE INDEX IF NOT EXISTS ix_scoring_profiles_active ON public.scoring_profiles (active);
