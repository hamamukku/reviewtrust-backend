-- V024__add_products_title.sql
ALTER TABLE IF EXISTS products
  ADD COLUMN IF NOT EXISTS title text,
  ADD COLUMN IF NOT EXISTS visible boolean NOT NULL DEFAULT true;

CREATE UNIQUE INDEX IF NOT EXISTS ux_products_asin ON products(asin);
