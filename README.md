# Backend

## Sakura 判定 (MVP)

- `ScoreService` は Amazon 由来のレビューを走査し、以下の 4 特徴量を算出して `review_scores` (`source = SITE`) に保存します。
  - `dist_bias` (★5率)、`duplicates` (最大クラスタ比率)、`surge` (日別急増度)、`noise` (URL/短文/記号連打)
- 閾値は `src/main/resources/scoring/thresholds.yml` の `feature_percent` / `sakura_percent` セクションで管理します。デフォルト値は教師データ 400 件で 60% 以上の精度になるよう設定しています。
- 判定結果 (`GENUINE` / `UNLIKELY` / `LIKELY` / `SAKURA`) と証跡 (`flags`, `rules`, `metrics`) は Scores API の `overall` ブロックとトップレベル `sakura_judge` に出力されます。

## 教師データ検証スクリプト

```
python backend/scripts/sakura_eval.py \
  --proof-root backend/delivery/proof \
  --thresholds backend/src/main/resources/scoring/thresholds.yml
```

- 指定ディレクトリ配下の `sakura / probably_sakura / probably_not_sakura / not_sakura` NDJSON を読み込み、同じ特徴量ロジックで混同行列と Accuracy を表示します。
- `PyYAML` が無い環境でも実行できるよう、閾値はスクリプト内にデフォルトを持っています（存在すれば thresholds.yml を優先）。
- Scores API は `display_score` (100 - risk score) を返却します。フロント側ではこの値を“信頼スコア”として表示します。
