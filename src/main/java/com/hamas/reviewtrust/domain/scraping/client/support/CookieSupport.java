package com.hamas.reviewtrust.domain.scraping.client.support;

/** Selenium未使用ビルド用のスタブ */
public final class CookieSupport {
  private CookieSupport() {}
  public static void applyCookieHeaderToDriver(Object ignored, String cookieHeader, String domain) {
    // Playwright版では未使用。必要時に実装差し替え。
  }
}
