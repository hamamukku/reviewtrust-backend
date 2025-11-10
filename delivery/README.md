# Delivery Package

## 1. 環境変数を設定
```
cp .env.sample .env
# SECURITY_JWT_SECRET を安全な Base64 文字列に置き換える
```

## 2. Docker Compose で起動
```
cd delivery
docker compose up -d --build
```

バックエンドはポート `8080`、PostgreSQL は `5432` で待ち受けます。

## 3. 管理フロー (PowerShell)
```
# 管理トークンを取得
./scripts/rt_login.ps1 -Email admin@example.com -Password <password>

# 教師データから商品登録とバッチ再取得
./scripts/rt_seed_products.ps1 -CsvPath ./data/labels.csv
./scripts/rt_rescrape_batch.ps1 -CsvPath ./scripts/products_map.csv -Limit 50

# 閾値再計算 + 混同行列
./scripts/rt_thresholds_build.ps1 -Force
./scripts/rt_pipeline.ps1 -ProductIdForProof <product-id>
```

## 4. OpenAPI/証跡
- `delivery/openapi.json` … `/v3/api-docs` のスナップショット
- `delivery/proof/` … `rt_collect_proof.ps1` により生成される証跡を格納
- `delivery/scoring/thresholds.yml` … 現行閾値

## 5. フロントエンド
```
cd review_trust_frontend_template
npm install
npm run build
npm run preview -- --host
```

ブラウザで `http://localhost:4173` を開き、API ベース URL を `.env.local` で指定します。
