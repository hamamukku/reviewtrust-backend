-- 公開APIで 410/Gone を判定しやすい VIEW（任意）
CREATE OR REPLACE VIEW public.v_products_public AS
SELECT p.*
  FROM public.products p
 WHERE p.visible = true
   AND p.publish_status = 'PUBLISHED';
