create table if not exists product_snapshots (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null references products(id) on delete cascade,
    source_url text,
    snapshot_json jsonb not null,
    source_html text,
    upload_target text,
    uploaded_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists ix_product_snapshots_product on product_snapshots(product_id);
