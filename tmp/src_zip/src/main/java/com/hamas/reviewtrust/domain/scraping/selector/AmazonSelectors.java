package com.hamas.reviewtrust.domain.scraping.selector;

/**
 * Amazon レビュー抽出用の堅牢セレクタ群。
 * 公式の data-hook を優先しつつ、クラシック/地域差の古いDOMにもフォールバックする。
 */
public final class AmazonSelectors {
  private AmazonSelectors() {}

  /** レビューブロック（1件分） */
  public static final String REVIEW_BLOCK = "div[data-hook=review], div.review";

  /** レビューIDを持つ属性候補（新旧DOM） */
  public static final String REVIEW_ID_ATTR = "data-review-id"; // 無ければ id="customer_review-XXXX" から抽出

  /** 星（例: "5つ星のうち4.0" / "4.0 out of 5 stars"） */
  public static final String STARS =
      "i[data-hook=review-star-rating] span.a-icon-alt, " +        // 現行
      "i[data-hook=cmps-review-star-rating] span.a-icon-alt, " +   // コンパクト
      "span[data-hook=rating-out-of-text], " +                     // 一部の代替
      "i.a-icon-star span.a-icon-alt";                             // 旧DOM

  /** タイトル */
  public static final String TITLE =
      "a[data-hook=review-title] span, " +
      "span[data-hook=review-title] span, " +
      "a[data-hook=review-title], " +
      "a.review-title";

  /** 本文（collapsed/expanded両対応） */
  public static final String BODY =
      "span[data-hook=review-body] span, " +
      "div.review-text-content span, " +
      "div[data-hook=review-collapsed] span, " +
      "span[data-hook=review-body]";

  /** レビュワー名 */
  public static final String REVIEWER = "span.a-profile-name";

  /** 認証バッジ（ベリファイド購入等） */
  public static final String VERIFIED =
      "span[data-hook=avp-badge], " +
      "span.a-size-mini.a-color-state";

  /** 投稿日（例: "2023年10月1日に日本でレビュー済み" / "Reviewed in Japan on October 1, 2023"） */
  public static final String DATE = "span[data-hook=review-date]";

  /** 参考になった票（例: "12人のお客様がこれが役に立ったと考えています" / "12 people found this helpful" / "One person found this helpful"） */
  public static final String HELPFUL = "span[data-hook=helpful-vote-statement], span.cr-vote-text";

  /** 添付画像 */
  public static final String IMAGES = "img[data-hook=review-image-tile], .review-image-tile img, img.review-image-tile";
}
