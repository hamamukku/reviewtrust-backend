-- V037__review_scores_score_numeric_to_integer.sql
-- score を numeric -> integer に型変更（USING で安全キャスト）
ALTER TABLE public.review_scores
  ALTER COLUMN score DROP DEFAULT,
  ALTER COLUMN score TYPE integer USING score::int;
