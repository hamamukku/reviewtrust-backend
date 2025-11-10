# ReviewTrust 実行プレイブック（PowerShell）

> 使い方：リポジトリ直下で PowerShell を開き、`.\scripts\rt_pipeline.ps1` から実行。  
> 個別のスクリプトは `rt_*.ps1 -?` で引数を確認。

## スクリプト一覧
- `rt_env.ps1` … 環境変数・Docker・DB起動チェック（idempotent）
- `rt_boot.ps1` … Spring Boot 起動（`gradle bootRun` に JWT 秘密鍵やログレベルを渡す）
- `rt_login.ps1` … 管理ログイン→トークン保存（`.token.jwt`）
- `rt_rescrape_batch.ps1` … CSV を読み batch で再スクレイプ（結果CSV出力）
- `rt_thresholds_build.ps1` … しきい値 YAML の生成（上書き可）
- `rt_collect_proof.ps1` … スコアJSON/OpenAPI/履歴を `delivery/proof/<timestamp>` に収集
- `rt_pipeline.ps1` … 上記を順に実行するオーケストレーション

## 例（最短）
```powershell
# 1) 起動（別ターミナルで動かしたい場合は、この行をコメントアウトし、単独で実行）
.\scripts\rt_boot.ps1 -Profile dev -Port 8080

# 2) ログイン
.\scripts\rt_login.ps1 -Email admin@example.com -Password <管理PW>

# 3) バッチ再スクレイプ
.\scripts\rt_rescrape_batch.ps1 -CsvPath .\scripts\products.csv -Limit 50

# 4) しきい値生成
.\scripts\rt_thresholds_build.ps1 -Force

# 5) 証跡収集（任意の productId）
.\scripts\rt_collect_proof.ps1 -ProductId <UUID or ASIN>
```

## CSV フォーマット
- ヘッダ例：`id,asin,url,name`
- `id` が無ければ `asin` を使います。`url` が無ければ `asin` から標準レビューURLを自動生成。

## 注意
- Windows PowerShell / PowerShell 7 どちらでも動作（ExecutionPolicy に注意）。
- Docker Desktop が必要。DB コンテナ名は `-DbContainer` で変更可能（デフォルト `reviewtrust_db`）。
- JWT 秘密鍵は存在しなければ自動生成（Base64）。`SECURITY_JWT_SECRET_ENCODING=base64` を前提に起動します。
