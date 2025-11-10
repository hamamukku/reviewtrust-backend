-- V025__add_products_url.sql
ALTER TABLE IF EXISTS products
  ADD COLUMN IF NOT EXISTS url text;
