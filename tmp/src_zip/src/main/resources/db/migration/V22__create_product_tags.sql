-- V22__create_product_tags.sql
-- products.id / tags.id の型に合わせて冪等的に product_tags を作る（安全策）

DO $$
DECLARE
  prod_udt text;
  tag_udt text;
  prod_sql_type text;
  tag_sql_type text;
  create_stmt text;
  fk_stmt text;
  has_products boolean;
  has_tags boolean;
BEGIN
  -- products.id の型を取得
  SELECT udt_name INTO prod_udt
  FROM information_schema.columns
  WHERE table_schema='public' AND table_name='products' AND column_name='id'
  LIMIT 1;

  SELECT udt_name INTO tag_udt
  FROM information_schema.columns
  WHERE table_schema='public' AND table_name='tags' AND column_name='id'
  LIMIT 1;

  IF prod_udt IS NULL THEN
    prod_udt := 'int8';
    RAISE NOTICE 'products.id not found — defaulting to int8 (bigint)';
  END IF;
  IF tag_udt IS NULL THEN
    tag_udt := 'int8';
    RAISE NOTICE 'tags.id not found — defaulting to int8 (bigint)';
  END IF;

  prod_sql_type := CASE prod_udt
    WHEN 'int8' THEN 'bigint'
    WHEN 'int4' THEN 'integer'
    WHEN 'uuid' THEN 'uuid'
    WHEN 'text' THEN 'text'
    ELSE 'bigint'
  END;

  tag_sql_type := CASE tag_udt
    WHEN 'int8' THEN 'bigint'
    WHEN 'int4' THEN 'integer'
    WHEN 'uuid' THEN 'uuid'
    WHEN 'text' THEN 'text'
    ELSE 'bigint'
  END;

  -- 内側のドル記法を $sql$ にしてネスト衝突を回避
  create_stmt := format($sql$CREATE TABLE IF NOT EXISTS public.product_tags (
      product_id %s NOT NULL,
      tag_id %s NOT NULL,
      created_at timestamptz DEFAULT now(),
      CONSTRAINT pk_product_tags PRIMARY KEY (product_id, tag_id)
  );$sql$, prod_sql_type, tag_sql_type);

  EXECUTE create_stmt;

  -- インデックス（なければ）
  IF NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_product_tags_product_id' AND n.nspname = 'public'
  ) THEN
    EXECUTE 'CREATE INDEX ix_product_tags_product_id ON public.product_tags (product_id);';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = 'ix_product_tags_tag_id' AND n.nspname = 'public'
  ) THEN
    EXECUTE 'CREATE INDEX ix_product_tags_tag_id ON public.product_tags (tag_id);';
  END IF;

  -- 外部キー追加（存在チェック）
  SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='products') INTO has_products;
  SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='tags') INTO has_tags;

  IF has_products THEN
    BEGIN
      fk_stmt := 'ALTER TABLE public.product_tags ADD CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;';
      IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.table_schema='public' AND tc.table_name='product_tags' AND tc.constraint_type='FOREIGN KEY' AND tc.constraint_name = 'fk_product_tags_product'
      ) THEN
        EXECUTE fk_stmt;
      END IF;
    EXCEPTION WHEN others THEN
      RAISE NOTICE 'Could not add FK fk_product_tags_product: %', SQLERRM;
    END;
  END IF;

  IF has_tags THEN
    BEGIN
      fk_stmt := 'ALTER TABLE public.product_tags ADD CONSTRAINT fk_product_tags_tag FOREIGN KEY (tag_id) REFERENCES public.tags(id) ON DELETE CASCADE;';
      IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.table_schema='public' AND tc.table_name='product_tags' AND tc.constraint_type='FOREIGN KEY' AND tc.constraint_name = 'fk_product_tags_tag'
      ) THEN
        EXECUTE fk_stmt;
      END IF;
    EXCEPTION WHEN others THEN
      RAISE NOTICE 'Could not add FK fk_product_tags_tag: %', SQLERRM;
    END;
  END IF;

  RAISE NOTICE 'product_tags ensured (product_id type=% / tag_id type=%)', prod_sql_type, tag_sql_type;
END$$;
