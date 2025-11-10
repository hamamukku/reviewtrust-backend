CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  event_time timestamptz NOT NULL DEFAULT now(),
  actor_username varchar(64),
  actor_user_id uuid,
  action varchar(64) NOT NULL,
  resource_type varchar(64),
  resource_id uuid,
  ip_address inet,
  user_agent text,
  detail jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_audit_logs_event_time ON public.audit_logs (event_time DESC);
CREATE INDEX IF NOT EXISTS ix_audit_logs_actor_username ON public.audit_logs (actor_username);
CREATE INDEX IF NOT EXISTS ix_audit_logs_action ON public.audit_logs (action);
