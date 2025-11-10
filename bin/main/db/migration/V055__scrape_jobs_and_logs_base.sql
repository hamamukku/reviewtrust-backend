-- V055__scrape_jobs_and_logs_base.sql（安全適用版）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ─────────────────────────────────────────────────────────────────────────────
-- scrape_jobs（既存なら何もしない）
CREATE TABLE IF NOT EXISTS public.scrape_jobs (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      uuid NOT NULL,
  source          text NOT NULL,
  requested_url   text,
  status          text NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  requested_by    text,
  started_at      timestamptz,
  finished_at     timestamptz,
  collected       integer DEFAULT 0,
  upserted        integer DEFAULT 0,
  message         text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_scrape_jobs_product ON public.scrape_jobs(product_id);
CREATE INDEX IF NOT EXISTS idx_scrape_jobs_status_created ON public.scrape_jobs(status, created_at DESC);

-- updated_at 自動更新トリガ（共通）
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'scrape_jobs_set_updated_at') THEN
    CREATE TRIGGER scrape_jobs_set_updated_at
      BEFORE UPDATE ON public.scrape_jobs
      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
  END IF;
END$$;

-- ─────────────────────────────────────────────────────────────────────────────
-- exception_logs（存在すれば列を増やす・無ければ作る）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='public' AND table_name='exception_logs'
  ) THEN
    CREATE TABLE public.exception_logs (
      id          bigserial PRIMARY KEY,
      ts          timestamptz NOT NULL DEFAULT now(),
      level       text NOT NULL DEFAULT 'ERROR',
      code        text,
      message     text,
      cause       text,
      stacktrace  text,
      request_id  text,
      context     jsonb
    );
  ELSE
    -- 必須列が無ければ追加（古いスキーマとの整合）
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='exception_logs' AND column_name='ts')
      THEN ALTER TABLE public.exception_logs ADD COLUMN ts timestamptz NOT NULL DEFAULT now();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='exception_logs' AND column_name='level')
      THEN ALTER TABLE public.exception_logs ADD COLUMN level text NOT NULL DEFAULT 'ERROR';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='exception_logs' AND column_name='context')
      THEN ALTER TABLE public.exception_logs ADD COLUMN context jsonb;
    END IF;
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_exception_logs_ts ON public.exception_logs(ts DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- audit_logs（存在すれば列を増やす・無ければ作る）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema='public' AND table_name='audit_logs'
  ) THEN
    CREATE TABLE public.audit_logs (
      id           bigserial PRIMARY KEY,
      ts           timestamptz NOT NULL DEFAULT now(),
      actor        text,
      action       text NOT NULL,
      entity_type  text,
      entity_id    uuid,
      request_id   text,
      payload      jsonb
    );
  ELSE
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='audit_logs' AND column_name='ts')
      THEN ALTER TABLE public.audit_logs ADD COLUMN ts timestamptz NOT NULL DEFAULT now();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='audit_logs' AND column_name='action')
      THEN ALTER TABLE public.audit_logs ADD COLUMN action text NOT NULL DEFAULT 'UNKNOWN';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='audit_logs' AND column_name='entity_type')
      THEN ALTER TABLE public.audit_logs ADD COLUMN entity_type text;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='audit_logs' AND column_name='entity_id')
      THEN ALTER TABLE public.audit_logs ADD COLUMN entity_id uuid;
    END IF;
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_audit_logs_ts     ON public.audit_logs(ts DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON public.audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs(entity_type, entity_id);
