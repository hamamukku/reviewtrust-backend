package com.hamas.reviewtrust.scraping;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin Playwright wrapper that handles the Amazon review page navigation and optional
 * interactive login flow. Callers are expected to honour Amazon's terms of service.
 */
public final class AmazonBrowserScraper {

    private static final Pattern STAR_PATTERN = Pattern.compile("([0-9]+(?:[\\.,][0-9])?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9０-９,，]+)");
    private static final Pattern UNIT_PRICE_PATTERN = Pattern.compile("([¥￥\\u00a5]?[0-9０-９,．\\.]+)\\s*[\\/／]\\s*([^\\s)]+)");
    private static final Pattern DISPLAY_PRICE_PATTERN = Pattern.compile("\"displayString\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern AMOUNT_PRICE_PATTERN = Pattern.compile("\"amount\"\\s*:\\s*([0-9.]+)");
    private static final int NAV_TIMEOUT_MS = 60_000;
    private static final int INLINE_REVIEW_LIMIT = 5;
    private static final String INLINE_REVIEW_CONTAINER_SELECTOR =
            "#cm-cr-dp-review-list div[data-hook=\"review\"], #reviewsMedley div[data-hook=\"review\"]";
    private static final String[] INLINE_REVIEW_RATING_SELECTORS = {
            "[data-hook=\"review-star-rating\"] span.a-icon-alt",
            "[data-hook=\"review-star-rating\"]",
            ".review-rating .a-icon-alt"
    };
    private static final String[] INLINE_REVIEW_TITLE_SELECTORS = {
            "[data-hook=\"review-title\"] span",
            "[data-hook=\"review-title\"]"
    };
    private static final String[] INLINE_REVIEW_BODY_SELECTORS = {
            "[data-hook=\"review-body\"] span",
            "[data-hook=\"review-body\"] div",
            "[data-hook=\"review-body\"]"
    };
    private static final String[] INLINE_REVIEW_REVIEWER_SELECTORS = {
            ".a-profile-name",
            "[data-hook=\"review-author\"]"
    };
    private static final String[] INLINE_REVIEW_DATE_SELECTORS = {
            "[data-hook=\"review-date\"]"
    };

    private static final String[] TITLE_SELECTORS = {
            "#productTitle",
            "#title span"
    };
    private static final String[] PRICE_NOW_SELECTORS = {
            "#corePriceDisplay_desktop_feature_div .priceToPay .a-offscreen",
            ".reinventPricePriceToPayMargin.priceToPay .a-offscreen",
            ".a-section .priceToPay .a-offscreen"
    };
    private static final String[] PRICE_LIST_SELECTORS = {
            "#corePriceDisplay_desktop_feature_div .basisPrice .a-offscreen",
            ".centralizedApexBasisPriceCSS .a-text-price .a-offscreen",
            "#pqv-price-list-price .a-text-strike"
    };
    private static final String[] UNIT_PRICE_VALUE_SELECTORS = {
            ".pricePerUnit .a-offscreen"
    };
    private static final String[] UNIT_PRICE_CONTAINER_SELECTORS = {
            ".pricePerUnit",
            "#pqv-price"
    };
    private static final String[] STAR_RATING_SELECTORS = {
            "#acrPopover .a-icon-alt",
            "i[data-hook=\"average-star-rating\"] .a-icon-alt"
    };
    private static final String[] REVIEW_COUNT_SELECTORS = {
            "#acrCustomerReviewText",
            "#acrCustomerReviewLink .a-link-normal"
    };
    private static final String[] MAIN_IMAGE_SELECTORS = {
            "#landingImage",
            "#imgTagWrapperId img"
    };
    private static final String[] ASIN_ATTR_SELECTORS = {
            "#price-block-container-0",
            "input#ASIN",
            "div[data-asin]"
    };

    private final Browser browser;

    private final BrowserContext context;
    private final Page page;
    private final AmazonLoginSelectors selectors = new AmazonLoginSelectors();
    private final CredentialProvider credentialProvider = new CredentialProvider();

    public AmazonBrowserScraper(Playwright playwright, boolean headless, Path statePath) {
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(headless);
        this.browser = playwright.chromium().launch(launch);

        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setLocale("ja-JP")
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
                ));

        if (statePath != null && Files.exists(statePath)) {
            options.setStorageStatePath(statePath);
        }

        this.context = browser.newContext(options);
        this.page = context.newPage();
        this.page.setDefaultTimeout(90_000);
    }

    private boolean isLoginWall(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/ap/signin")
                || lower.contains("captcha")
                || lower.contains("challenges")
                || lower.contains("/errors/validatecaptcha");
    }

    private boolean requiresFallback(String url, String html) {
        if (isLoginWall(url)) {
            return true;
        }
        if (html == null) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("captcha")
                || lower.contains("robot check")
                || lower.contains("enter the characters you see below");
    }

    public ReviewsResult fetchReviewsHtml(String asin, boolean allowInteractiveLogin, Path statePath) {
        Objects.requireNonNull(asin, "asin is required");
        String reviewsUrl = "https://www.amazon.co.jp/product-reviews/" + asin
                + "/?reviewerType=all_reviews&sortBy=recent";

        navigateWithGrace(reviewsUrl);

        String html = safeContent();
        boolean fallbackNeeded = requiresFallback(page.url(), html);
        boolean attemptedLogin = false;

        if (fallbackNeeded && allowInteractiveLogin) {
            System.out.println("[REVIEWS] sign-in wall detected, attempting automated login");
            ensureLoggedIn();
            attemptedLogin = true;
            navigateWithGrace(reviewsUrl);
            html = safeContent();
            fallbackNeeded = requiresFallback(page.url(), html);
        }

        ProductPageCapture productPage = null;
        boolean fallbackUsed = false;
        if (fallbackNeeded) {
            System.out.println("[REVIEWS] sign-in wall detected -> fallback to /dp");
            productPage = fetchProductPage(asin);
            fallbackUsed = true;
            html = null;
        } else {
            System.out.println("[DP] fallback=skipped (attemptedLogin=" + attemptedLogin + ")");
        }

        if (statePath != null) {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
        }
        return new ReviewsResult(html, productPage != null ? productPage.html : null,
                productPage != null ? productPage.pageUrl : null, fallbackUsed);
    }

    private ProductPageCapture fetchProductPage(String asin) {
        Objects.requireNonNull(asin, "asin is required");
        String productUrl = "https://www.amazon.co.jp/dp/" + asin;
        System.out.println("[DP] open " + productUrl);

        navigateWithGrace(productUrl);
        String currentUrl = page.url();
        if (isLoginWall(currentUrl)) {
            throw new IllegalStateException("Product page redirected to login wall for asin=" + asin);
        }

        String html = safeContent();
        if (requiresFallback(currentUrl, html)) {
            throw new IllegalStateException("Product page blocked by captcha/sign-in for asin=" + asin);
        }
        return new ProductPageCapture(currentUrl, html);
    }

    public void close() {
        page.close();
        context.close();
        browser.close();
    }

    private void ensureLoggedIn() {
        if (!isLoginWall(page.url())) {
            return;
        }

        System.out.println("[AmazonBrowserScraper] Login wall detected. Attempting automated sign-in...");

        String email = credentialProvider.resolveEmail();
        String password = credentialProvider.resolvePassword();

        try {
            selectors.fillEmail(page, email);
            selectors.clickContinue(page);
            page.waitForURL("**/ap/signin**", new Page.WaitForURLOptions().setTimeout(10_000));
            selectors.fillPassword(page, password);
            selectors.submitSignin(page);
        } catch (PlaywrightException e) {
            System.out.println("[AmazonBrowserScraper] Navigation interrupted during automated login: " + e.getMessage());
        }
        selectors.handleChallengesIfPresent(page, Duration.ofSeconds(60));
        waitForLoginCompletion();
    }

    private void navigateWithGrace(String url) {
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            String message = String.valueOf(e.getMessage());
            if (message == null || !message.contains("ERR_ABORTED")) {
                throw e;
            }
        }
    }

    private String safeContent() {
        try {
            return page.content();
        } catch (PlaywrightException e) {
            return null;
        }
    }

    private String firstText(String[] selectors) {
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                if (locator.count() == 0) {
                    continue;
                }
                Locator first = locator.first();
                String text = first.innerText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (PlaywrightException ignored) {
            }
        }
        return null;
    }

    private String firstText(Locator root, String[] selectors) {
        if (root == null) {
            return null;
        }
        for (String selector : selectors) {
            try {
                Locator locator = root.locator(selector);
                if (locator.count() == 0) {
                    continue;
                }
                Locator first = locator.first();
                String text = first.innerText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (PlaywrightException ignored) {
            }
        }
        return null;
    }

    private String firstAttribute(String attribute, String[] selectors) {
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                if (locator.count() == 0) {
                    continue;
                }
                Locator first = locator.first();
                String value = first.getAttribute(attribute);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (PlaywrightException ignored) {
            }
        }
        return null;
    }

    private String resolveAsinFromPage() {
        String value = firstAttribute("data-csa-c-asin", new String[]{ASIN_ATTR_SELECTORS[0]});
        if (value != null) {
            return value;
        }
        value = firstAttribute("value", new String[]{ASIN_ATTR_SELECTORS[1]});
        if (value != null) {
            return value;
        }
        return firstAttribute("data-asin", new String[]{ASIN_ATTR_SELECTORS[2]});
    }

    private BigDecimal priceFromSelectors(String[] selectors) {
        String text = normalizeWhitespace(firstText(selectors));
        return parseCurrency(text);
    }

    private BigDecimal extractPriceFromJson(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher display = DISPLAY_PRICE_PATTERN.matcher(html);
        while (display.find()) {
            BigDecimal parsed = parseCurrency(display.group(1));
            if (parsed != null) {
                return parsed;
            }
        }
        Matcher amount = AMOUNT_PRICE_PATTERN.matcher(html);
        if (amount.find()) {
            try {
                return new BigDecimal(amount.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private BigDecimal parseCurrency(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(text);
        normalized = normalized.replace("￥", "")
                .replace("¥", "")
                .replaceAll("[^0-9.,]", "");
        normalized = normalized.replace("，", "").replace(",", "");
        normalized = normalized.replace("．", ".").replace("｡", ".");
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.indexOf('.') != normalized.lastIndexOf('.')) {
            normalized = normalized.substring(0, normalized.lastIndexOf('.')).replace(".", "") +
                    normalized.substring(normalized.lastIndexOf('.'));
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(text);
        normalized = normalized.replaceAll("[^0-9]", "");
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseStarRating(String text) {
        if (text == null) {
            return null;
        }
        String normalized = normalizeDigits(text);
        Matcher matcher = STAR_PATTERN.matcher(normalized.replace(',', '.'));
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String normalizeImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("http")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return "https://www.amazon.co.jp" + trimmed;
        }
        return trimmed;
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return null;
        }
        return text.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    private String normalizeDigits(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch >= '０' && ch <= '９') {
                sb.append((char) (ch - '０' + '0'));
            } else if (ch == '．') {
                sb.append('.');
            } else if (ch == '，') {
                sb.append(',');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private void waitForLoginCompletion() {
        for (int i = 0; i < 60; i++) {
            String currentUrl = page.url();
            if (!isLoginWall(currentUrl)) {
                System.out.println("[AmazonBrowserScraper] Login confirmed. Current URL: " + currentUrl);
                return;
            }
            page.waitForTimeout(1_000);
        }
        throw new RuntimeException("Login failed or still on sign-in wall after timeout.");
    }

    public static final class ReviewsResult {
        private final String reviewsHtml;
        private final String productPageHtml;
        private final String productPageUrl;
        private final boolean fallbackUsed;

        public ReviewsResult(String reviewsHtml,
                             String productPageHtml,
                             String productPageUrl,
                             boolean fallbackUsed) {
            this.reviewsHtml = reviewsHtml;
            this.productPageHtml = productPageHtml;
            this.productPageUrl = productPageUrl;
            this.fallbackUsed = fallbackUsed;
        }

        public String getReviewsHtml() {
            return reviewsHtml;
        }

        public String getProductPageHtml() {
            return productPageHtml;
        }

        public String getProductPageUrl() {
            return productPageUrl;
        }

        public boolean isFallbackUsed() {
            return fallbackUsed;
        }
    }

    private static final class ProductPageCapture {
        private final String pageUrl;
        private final String html;

        private ProductPageCapture(String pageUrl, String html) {
            this.pageUrl = pageUrl;
            this.html = html;
        }
    }
}
