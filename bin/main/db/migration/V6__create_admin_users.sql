-- V6__create_admin_users.sql
-- Create admin_users table used by AuthService / AdminUser entity.
-- Note: requires pgcrypto extension for gen_random_uuid(). If unavailable you can remove the DEFAULT and supply UUIDs from the application.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.admin_users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(32) NOT NULL,
    password_hash text NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- username はユニーク制約（認証時の検索で利用）
CREATE UNIQUE INDEX IF NOT EXISTS ux_admin_users_username ON public.admin_users (username);

