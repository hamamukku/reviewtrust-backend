# ReviewTrust 実行スクリプト (PowerShell)

> 実運用では PowerShell から `./scripts/rt_pipeline.ps1` を実行します。個別の補助スクリプトは `rt_*.ps1 -?` でヘルプを確認できます。

## スクリプト一覧
- `rt_env.ps1` … 環境変数・Docker・DB の起動チェック (idempotent)
- `rt_boot.ps1` … Spring Boot 起動 (`gradle bootRun` 相当 / JWT シークレットのログ抑制)
- `rt_login.ps1` … 管理者ログインしてトークン保存 (`token.jwt`)
- `rt_seed_products.ps1` … `data/labels.csv` から商品登録 → `products_map.csv` 出力
- `rt_rescrape_batch.ps1` … CSV を用いてバッチで再取得 (結果 CSV 出力)
- `rt_thresholds_build.ps1` … 閾値 YAML の生成 (再実行安全)
- `rt_features_sql.sql` … 特徴量抽出用 SQL (psql で `\\copy`)
- `rt_confusion_sql.sql` … 混同行列/精度算出 SQL
- `rt_collect_proof.ps1` … スコア JSON / OpenAPI / 操作履歴を `delivery/proof/<timestamp>` に保存
- `rt_pipeline.ps1` … 上記一連を順に実行する総合パイプライン

## 例
```powershell
# 1) 開発サーバー起動 (ターミナルを分ける場合はコメントアウト)
./scripts/rt_boot.ps1 -Profile dev -Port 8080

# 2) 管理ログイン
./scripts/rt_login.ps1 -Email admin@example.com -Password <管理パスワード>

# 3) 教師データから商品を登録し CSV を生成
./scripts/rt_seed_products.ps1 -CsvPath ./data/labels.csv -BaseUrl http://localhost:8080

# 4) 生成された CSV でバッチ再取得
./scripts/rt_rescrape_batch.ps1 -CsvPath ./scripts/products_map.csv -Limit 50

# 5) 閾値 YAML を再生成
./scripts/rt_thresholds_build.ps1 -Force

# 6) (任意) psql が使える場合、特徴量・混同行列 CSV を出力
psql -f scripts/rt_features_sql.sql
psql -f scripts/rt_confusion_sql.sql

# 7) 任意商品の証跡を収集
./scripts/rt_collect_proof.ps1 -ProductId <UUID>
```

## CSV フォーマット
- `data/labels.csv` … `url,label` 列で教師ラベルを定義
- `products_map.csv` … シード処理で生成される `id,url,name,createdAt`

## 注意
- Windows PowerShell / PowerShell 7 どちらでも動作 (ExecutionPolicy に注意)
- Docker Desktop が必要。既存 DB コンテナ名は `-DbContainer` で変更可能
- JWT シークレットは欠如時に自動生成 (Base64)。`SECURITY_JWT_SECRET_ENCODING=base64` をセット
