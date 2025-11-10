package com.hamas.reviewtrust.domain.scraping.client;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Amazon レビュー取得クライアント（Playwright専用）
 *
 * サービス層が期待する互換API：
 *  - String buildReviewsUrlFromAsin(String asin, Locale locale)
 *  - String fetchHtmlByAsin(String asin, Locale locale, int limit)
 *  - String fetchHtmlByUrl (String url,                int limit)
 *
 * 追加API（デバッグ用）：
 *  - List<String> fetchReviewBlocks(String inputUrlOrAsin, Locale locale, int limit)
 *
 * 起動プロパティ（優先順位：JVMシステムプロパティ > 環境変数 > 既定）
 *  - app.scraping.headless   / APP_SCRAPING_HEADLESS   / 既定: true
 *  - app.scraping.channel    / APP_SCRAPING_CHANNEL    / 既定: (未指定＝デフォルト)
 *  - PLAYWRIGHT_BROWSERS_PATH（環境変数）           / 既定: Playwright のデフォルトディレクトリ
 */
public class AmazonReviewClient implements AutoCloseable {
  // 対象ASIN抽出用
  private static final Pattern P_DP   = Pattern.compile("/dp/([A-Z0-9]{10})");
  private static final Pattern P_REV  = Pattern.compile("/product-reviews/([A-Z0-9]{10})");
  private static final Pattern P_ASIN = Pattern.compile("\\b([A-Z0-9]{10})\\b");

  private static final String UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final Playwright pw;
  private final Browser browser;

  /** 既定: -Dapp.scraping.headless=true を既定に、falseならUI表示 */
  public AmazonReviewClient() {
    // ---- 外部制御（ブラウザパス） ----
    String browsersPath = firstNonBlank(
        System.getProperty("playwright.browsersPath"),
        System.getenv("PLAYWRIGHT_BROWSERS_PATH")
    );
    if (browsersPath != null) {
      System.setProperty("playwright.browsersPath", browsersPath);
      log("[PW] browsersPath=%s", browsersPath);
    } else {
      log("[PW] browsersPath=<default>");
    }

    // ---- 外部制御（ヘッドレス / チャネル） ----
    boolean headless = parseBool(
        System.getProperty("app.scraping.headless"),
        System.getenv("APP_SCRAPING_HEADLESS"),
        true // default
    );
    String channel = firstNonBlank(
        System.getProperty("app.scraping.channel"),
        System.getenv("APP_SCRAPING_CHANNEL")
    );

    this.pw = Playwright.create();
    BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
        .setHeadless(headless)
        // ボット判定の一部回避（“効く時は効く”程度）
        .setArgs(Arrays.asList("--disable-blink-features=AutomationControlled",
                               "--disable-dev-shm-usage", "--no-sandbox"));

    if (channel != null && !channel.isBlank()) {
      try {
        // Playwright 1.40+ : setChannel は文字列を受け付ける
        opts.setChannel(channel);
        log("[PW] launch headless=%s channel=%s", headless, channel);
      } catch (Throwable t) {
        log("[PW] channel='%s' の適用に失敗：%s（既定で起動）", channel, t.toString());
      }
    } else {
      log("[PW] launch headless=%s channel=<default>", headless);
    }

    this.browser = pw.chromium().launch(opts);
  }

  // ========= 互換API（サービス層が呼ぶ） =========

  /** ロケールからレビュー一覧URLを生成 */
  public String buildReviewsUrlFromAsin(String asin, Locale locale) {
    Locale loc = (locale != null) ? locale : Locale.JAPAN;
    String domain = domainFor(loc);
    String lang   = languageParamFor(loc);
    return "https://" + domain + "/product-reviews/" + asin
        + "/?sortBy=recent&reviewerType=all_reviews&language=" + lang + "&pageNumber=1";
  }

  /** ASIN + Locale → レビューHTML（結合1ドキュメント） */
  public String fetchHtmlByAsin(String asin, Locale locale, int limit) {
    String url = buildReviewsUrlFromAsin(asin, locale);
    List<String> blocks = fetchFromReviewsUrl(url, limit, locale);
    return joinAsHtmlPage(blocks);
  }

  /** URL（/dp/** or /product-reviews/**）→ レビューHTML（結合1ドキュメント） */
  public String fetchHtmlByUrl(String url, int limit) {
    Locale loc = guessLocaleFromUrl(url);
    if (url != null && url.contains("/product-reviews/")) {
      return joinAsHtmlPage(fetchFromReviewsUrl(url, limit, loc));
    }
    String asin = extractAsin(url);
    String reviewsUrl = buildReviewsUrlFromAsin(asin, loc);
    return joinAsHtmlPage(fetchFromReviewsUrl(reviewsUrl, limit, loc));
  }

  // ========= 追加API（デバッグ用） =========

  public List<String> fetchReviewBlocks(String inputUrlOrAsin, Locale locale, int limit) {
    Locale loc = (locale != null) ? locale : Locale.JAPAN;
    String asin = extractAsin(inputUrlOrAsin);
    String url  = buildReviewsUrlFromAsin(asin, loc);
    return fetchFromReviewsUrl(url, limit, loc);
  }

  // ========= 実処理 =========

  private List<String> fetchFromReviewsUrl(String reviewsUrl, int limit, Locale locale) {
    Locale loc = (locale != null) ? locale : guessLocaleFromUrl(reviewsUrl);
    String acceptLanguage = acceptLanguageFor(loc);

    RuntimeException lastError = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try (BrowserContext ctx = newContext(loc);
           Page page = ctx.newPage()) {

        page.setExtraHTTPHeaders(Map.of("Accept-Language", acceptLanguage));

        log("[NAV] %s (try=%d)", reviewsUrl, attempt);
        page.navigate(reviewsUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForTimeout(500);

        // Cookie/同意ダイアログがあれば潰す（存在しない場合は無害）
        clickIfExists(page, "text=同意して続行", 1500);
        clickIfExists(page, "#sp-cc-accept", 1500);
        clickIfExists(page, "input[name='accept']", 1500);
        clickIfExists(page, ".a-button-primary:has-text('同意')", 1500);
        page.waitForTimeout(250);

        // サインイン・ロボ判定に飛ばされたらデバッグ保存して中断
        if (isSignInOrRobot(page)) {
          dump(page, "build/last-amzn.signin.html");
          throw new IllegalStateException("Redirected to sign-in / robot check: " + page.url());
        }

        // レビュー要素を待機（最大8秒）
        page.waitForSelector("div[data-hook='review']",
            new Page.WaitForSelectorOptions().setTimeout(8000));

        // 遅延ロード対策（数回スクロール）
        for (int i = 0; i < 5; i++) {
          page.mouse().wheel(0, 1600);
          page.waitForTimeout(300);
        }

        Locator cards = page.locator("div[data-hook='review']");
        int found = cards.count();
        if (found == 0) {
          // 何も無い時はページ全体をダンプして返す（パーサ側で原因把握用）
          dump(page, "build/last-amzn.html");
          log("[PARSE] review cards NOT FOUND");
          return List.of(page.content());
        }

        int n = Math.min(found, Math.max(1, limit));
        List<String> blocks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
          ElementHandle h = cards.nth(i).elementHandle();
          String cardHtml;
          if (h != null) {
            Object rv = h.evaluate("el => el.outerHTML");
            cardHtml = (rv instanceof String) ? (String) rv : cards.nth(i).innerHTML();
          } else {
            cardHtml = "<div data-hook=\"review\">" + cards.nth(i).innerHTML() + "</div>";
          }
          blocks.add(cardHtml);
        }
        log("[PARSE] collected=%d limit=%d url=%s", blocks.size(), limit, reviewsUrl);
        return blocks;

      } catch (Exception e) {
        lastError = new RuntimeException("attempt " + attempt + " failed: " + e.getMessage(), e);
        log("[ERR] %s", lastError.toString());
        try { Thread.sleep(650L * attempt); } catch (InterruptedException ignored) {}
      }
    }
    // ここまで来たら失敗
    if (lastError != null) {
      try {
        Files.writeString(Path.of("build/last-amzn.error.html"),
            "ERROR: " + lastError + "\n", StandardCharsets.UTF_8);
      } catch (Exception ignore) {}
      throw lastError;
    }
    throw new RuntimeException("Unknown error in fetchFromReviewsUrl");
  }

  /** ブロック配列を1つのHTMLに結合（既存パーサ互換） */
  private static String joinAsHtmlPage(List<String> blocks) {
    if (blocks == null || blocks.isEmpty()) return "<!doctype html><html><body></body></html>";
    StringBuilder sb = new StringBuilder(256 * blocks.size());
    sb.append("<!doctype html><html><head><meta charset=\"utf-8\"></head><body>");
    sb.append("<div id=\"rt-joined\">");
    for (String b : blocks) sb.append(b).append('\n');
    sb.append("</div></body></html>");
    return sb.toString();
  }

  private BrowserContext newContext(Locale locale) {
    String localeTag = (locale != null ? locale.toLanguageTag() : "ja-JP");
    return browser.newContext(new Browser.NewContextOptions()
        .setLocale(localeTag)
        .setUserAgent(UA)
        .setViewportSize(1280, 900));
  }

  private static boolean isSignInOrRobot(Page page) {
    String u = page.url().toLowerCase(Locale.ROOT);
    String t = "";
    try { t = page.title(); } catch (Throwable ignore) {}
    if (u.contains("ap/signin") || u.contains("verification") || u.contains("captcha") || u.contains("gp/browse.html"))
      return true;
    return t.contains("サインイン") || t.toLowerCase(Locale.ROOT).contains("sign in");
  }

  private static void clickIfExists(Page page, String selector, int timeoutMs) {
    try {
      page.locator(selector).first().click(new Locator.ClickOptions().setTimeout(timeoutMs));
    } catch (Throwable ignore) {}
  }

  private static void dump(Page page, String path) {
    try {
      Files.writeString(Path.of(path), page.content(), StandardCharsets.UTF_8);
    } catch (Throwable ignore) {}
  }

  // ========= URL/Locale ユーティリティ =========

  private static String extractAsin(String input) {
    if (input == null) return "";
    Matcher m1 = P_DP.matcher(input);
    if (m1.find()) return m1.group(1);
    Matcher m2 = P_REV.matcher(input);
    if (m2.find()) return m2.group(1);
    Matcher m3 = P_ASIN.matcher(input);
    if (m3.find()) return m3.group(1);
    return input; // 最終手段
  }

  private static String domainFor(Locale loc) {
    String c = (loc != null) ? loc.getCountry() : "JP";
    if ("JP".equalsIgnoreCase(c)) return "www.amazon.co.jp";
    if ("US".equalsIgnoreCase(c)) return "www.amazon.com";
    if ("GB".equalsIgnoreCase(c)) return "www.amazon.co.uk";
    if ("DE".equalsIgnoreCase(c)) return "www.amazon.de";
    if ("FR".equalsIgnoreCase(c)) return "www.amazon.fr";
    if ("IT".equalsIgnoreCase(c)) return "www.amazon.it";
    if ("ES".equalsIgnoreCase(c)) return "www.amazon.es";
    if ("CA".equalsIgnoreCase(c)) return "www.amazon.ca";
    if ("AU".equalsIgnoreCase(c)) return "www.amazon.com.au";
    if ("IN".equalsIgnoreCase(c)) return "www.amazon.in";
    return "www.amazon.co.jp";
  }

  private static String languageParamFor(Locale loc) {
    String lang = (loc != null && loc.getLanguage() != null && !loc.getLanguage().isEmpty())
        ? loc.getLanguage() : "ja";
    String cnt  = (loc != null && loc.getCountry() != null && !loc.getCountry().isEmpty())
        ? loc.getCountry()  : "JP";
    return (lang + "_" + cnt);
  }

  private static String acceptLanguageFor(Locale loc) {
    String primary = (loc != null ? loc.toLanguageTag() : "ja-JP");
    String fallback = primary.split("-")[0];
    return primary + "," + fallback + ";q=0.9,en-US;q=0.8,en;q=0.7";
  }

  private static Locale guessLocaleFromUrl(String url) {
    if (url == null) return Locale.JAPAN;
    String u = url.toLowerCase(Locale.ROOT);
    if (u.contains("amazon.co.jp")) return Locale.JAPAN;
    if (u.contains("amazon.com.au")) return new Locale.Builder().setLanguage("en").setRegion("AU").build();
    if (u.contains("amazon.co.uk")) return Locale.UK;
    if (u.contains("amazon.com"))   return Locale.US;
    if (u.contains("amazon.de"))    return Locale.GERMANY;
    if (u.contains("amazon.fr"))    return Locale.FRANCE;
    if (u.contains("amazon.it"))    return Locale.ITALY;
    if (u.contains("amazon.es"))    return new Locale.Builder().setLanguage("es").setRegion("ES").build();
    if (u.contains("amazon.ca"))    return Locale.CANADA;
    if (u.contains("amazon.in"))    return new Locale.Builder().setLanguage("hi").setRegion("IN").build();
    return Locale.JAPAN;
  }

  @Override public void close() {
    try { if (browser != null) browser.close(); } catch (Throwable ignore) {}
    try { if (pw != null) pw.close(); } catch (Throwable ignore) {}
  }

  // ---- util ----
  private static void log(String fmt, Object... args) {
    try {
      System.out.println("[AmazonReviewClient] " + String.format(Locale.ROOT, fmt, args));
    } catch (Throwable ignore) {}
  }
  private static boolean parseBool(String sysProp, String env, boolean def) {
    String v = firstNonBlank(sysProp, env);
    if (v == null) return def;
    return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
  }
  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }
}
