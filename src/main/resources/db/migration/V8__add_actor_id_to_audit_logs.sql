-- V8__add_actor_id_to_audit_logs.sql
-- 目的: Hibernate が期待する actor_id を追加し、既存 actor_user_id があればコピーする。

ALTER TABLE public.audit_logs
  ADD COLUMN IF NOT EXISTS actor_id uuid;

-- 既存データがあれば移行
UPDATE public.audit_logs
SET actor_id = actor_user_id
WHERE actor_id IS NULL AND actor_user_id IS NOT NULL;

-- インデックス（検索用）
CREATE INDEX IF NOT EXISTS ix_audit_logs_actor_id ON public.audit_logs (actor_id);

-- （必要なら）外部キーを貼る場合の例（コメントアウト）
-- ALTER TABLE public.audit_logs
--   ADD CONSTRAINT fk_audit_actor_user FOREIGN KEY (actor_id) REFERENCES public.admin_users(id);
