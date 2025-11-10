package com.hamas.reviewtrust.scraping;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Handles Playwright lifecycle and keeps the login session via storageState.
 */
public class AmazonBrowserClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AmazonBrowserClient.class);
    private static final String AMAZON_HOME = "https://www.amazon.co.jp/";
    private static final List<String> CONSENT_SELECTORS = List.of(
            "button:has-text(\"同意して続行\")",
            "#sp-cc-accept"
    );
    private static final List<String> OTP_SELECTORS = List.of(
            "#auth-mfa-otpcode",
            "input[name='otpCode']",
            "input#cvf-input-code",
            "input[name='code']"
    );
    private static final List<String> CONTINUE_SELECTORS = List.of(
            "#continue",
            "input#continue",
            "input[type='submit'][aria-labelledby='continue-announce']"
    );
    private static final List<String> SIGN_IN_SELECTORS = List.of(
            "#signInSubmit",
            "input#signInSubmit",
            "button#signInSubmit"
    );
    private static final List<String> LOGIN_LINK_SELECTORS = List.of(
            "a[data-nav-role='signin']",
            "#nav-link-accountList a",
            "a[href*='/ap/signin']"
    );
    private static final List<String> PASSKEY_SELECTORS = List.of(
            "#auth-signin-via-passkey-btn",
            "#continue:has-text(\"パスキーでサインイン\")"
    );
    private static final List<String> CAPTCHA_SELECTORS = List.of(
            "iframe[src*='captcha']",
            "img[src*='captcha']",
            "input[name='cvf_captcha_input']"
    );
    private static final List<String> MFA_SUBMIT_SELECTORS = List.of(
            "#auth-signin-button",
            "input#auth-signin-button",
            "span.a-button-inner input[type='submit']"
    );
    private static final String EMAIL_FIELD_SELECTOR =
            "input#ap_email_login:visible, input[name='email']:not([type='hidden']):visible";
    private static final String PASSWORD_FIELD_SELECTOR =
            "input#ap_password:visible, input[name='password']:visible";
    private static final Duration LOGIN_SELECTOR_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration LOGIN_LINK_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration OTP_WAIT_TIMEOUT = Duration.ofSeconds(120);

    private final ScrapingProps props;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private boolean opened;

    public AmazonBrowserClient(ScrapingProps props) {
        this.props = props;
    }

    public synchronized BrowserContext openContext() {
        return openContext(props.isEnableBrowserLogin());
    }

    public synchronized BrowserContext openContext(boolean allowInteractiveLogin) {
        if (opened) {
            return context;
        }
        try {
            playwright = Playwright.create();
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(props.isHeadless());
            String channel = props.getChannel();
            if (channel != null && !channel.isBlank()) {
                launchOptions.setChannel(channel);
            }
            browser = playwright.chromium().launch(launchOptions);

            Path storagePath = resolveStoragePath();
            boolean hasStorage = Files.exists(storagePath);
            boolean loginPerformed = false;

            Browser.NewContextOptions ctxOptions = new Browser.NewContextOptions();
            ctxOptions.setLocale("ja-JP");
            ctxOptions.setExtraHTTPHeaders(Map.of(
                    "Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"
            ));
            if (hasStorage) {
                ctxOptions.setStorageStatePath(storagePath);
                log.info("event=STORAGESTATE_LOADED path={}", storagePath);
            } else {
                log.info("event=STORAGESTATE_MISSING path={}", storagePath);
            }

            context = browser.newContext(ctxOptions);
            page = context.newPage();
            page.setDefaultTimeout(Duration.ofSeconds(30).toMillis());
            page.navigate(AMAZON_HOME);
            waitForDomReady(page);
            maybeAcceptConsent(page);

            boolean loggedIn = isLoggedIn(page);
            if (!loggedIn && !allowInteractiveLogin) {
                log.info("event=LOGIN_SKIPPED reason=interactive_login_disabled");
                if (!hasStorage) {
                    throw new IllegalStateException("Interactive login disabled and storage state file is missing");
                }
                throw new IllegalStateException("Interactive login disabled but Amazon session is not active");
            }

            if (!loggedIn) {
                ensureCredentials();
                performLogin(page);
                waitForDomReady(page);
                if (!isLoggedIn(page)) {
                    throw new IllegalStateException("Amazon login verification failed");
                }
                loginPerformed = true;
                log.info("event=LOGIN_PERFORMED user={}", maskEmail(props.getAmazonEmail()));
            } else {
                log.debug("event=LOGIN_FLOW session_active_from_storage=true");
            }

            if (loginPerformed || (!hasStorage && allowInteractiveLogin)) {
                persistStorageState(storagePath);
            }
            opened = true;
            return context;
        } catch (RuntimeException e) {
            String message = Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            log.error("event=SCRAPE_ABORTED message={}", message, e);
            closeSilently();
            throw e;
        }
    }

    public Page getPage() {
        return page;
    }

    public BrowserContext getContext() {
        return context;
    }

    @Override
    public void close() {
        closeSilently();
    }

    private void closeSilently() {
        closeQuietly(page);
        closeQuietly(context);
        closeQuietly(browser);
        closeQuietly(playwright);
        page = null;
        context = null;
        browser = null;
        playwright = null;
    }

    private Path resolveStoragePath() {
        Path storagePath = Path.of(props.getStorageStatePath()).toAbsolutePath().normalize();
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare storage state directory", e);
        }
        return storagePath;
    }

    private void maybeAcceptConsent(Page page) {
        for (String selector : CONSENT_SELECTORS) {
            try {
                if (page.locator(selector).count() > 0 && page.locator(selector).first().isVisible()) {
                    page.locator(selector).first().click();
                    waitForDomReady(page);
                    log.info("event=CONSENT_CLICKED selector={}", selector);
                    return;
                }
            } catch (PlaywrightException e) {
                log.debug("Consent selector not clickable selector={} message={}", selector, e.getMessage());
            }
        }
    }

    private boolean isLoggedIn(Page page) {
        try {
            Locator navLine = page.locator("#nav-link-accountList-nav-line-1");
            if (safeCount(navLine) > 0) {
                String text = navLine.first().innerText().toLowerCase(Locale.ROOT);
                if (text.contains("sign in") || text.contains("ログイン") || text.contains("サインイン")) {
                    return false;
                }
                return true;
            }
            Locator locator = page.locator("#nav-link-accountList");
            if (safeCount(locator) == 0) {
                return false;
            }
            String text = locator.first().innerText().toLowerCase(Locale.ROOT);
            return !(text.contains("sign in") || text.contains("ログイン") || text.contains("サインイン"));
        } catch (PlaywrightException e) {
            log.debug("Failed to resolve login status message={}", e.getMessage());
            return false;
        }
    }

    private void ensureCredentials() {
        if (!StringUtils.hasText(props.getAmazonEmail()) || !StringUtils.hasText(props.getAmazonPassword())) {
            throw new IllegalStateException("Amazon credentials are required for initial login");
        }
    }

    private void performLogin(Page page) {
        maybeAcceptConsent(page);
        detectCaptchaOrThrow(page);

        LocatorSelection loginSelection = waitForLoginLink(page);
        try {
            loginSelection.locator.click();
        } catch (PlaywrightException e) {
            log.info("event=LOGIN_CLICK_TIMEOUT method={} timeoutMs={} message={}",
                    loginSelection.selector, LOGIN_LINK_TIMEOUT.toMillis(), e.getMessage());
            throw new IllegalStateException("Amazon login link click failed", e);
        }
        try {
            page.waitForURL("**/ap/signin**",
                    new Page.WaitForURLOptions().setTimeout(LOGIN_LINK_TIMEOUT.toMillis()));
        } catch (PlaywrightException e) {
            log.info("event=LOGIN_NAV_TIMEOUT expected=**/ap/signin** timeoutMs={} message={}",
                    LOGIN_LINK_TIMEOUT.toMillis(), e.getMessage());
            throw new IllegalStateException("Amazon sign-in navigation timeout", e);
        }
        waitForDomReady(page);
        maybeAcceptConsent(page);
        detectCaptchaOrThrow(page);

        Locator emailField = findFirstVisible(page, EMAIL_FIELD_SELECTOR);
        if (emailField != null) {
            emailField.fill(props.getAmazonEmail());
            Locator continueButton = findFirstVisible(page, CONTINUE_SELECTORS);
            if (continueButton == null) {
                throw new IllegalStateException("Amazon login continue control not found");
            }
            continueButton.click();
            waitForDomReady(page);
            maybeAcceptConsent(page);
        } else {
            log.info("event=LOGIN_FLOW email_hidden_or_prefilled=true");
        }

        logPasskeyPromptIfPresent(page);
        detectCaptchaOrThrow(page);

        page.waitForSelector(
                PASSWORD_FIELD_SELECTOR,
                new Page.WaitForSelectorOptions().setTimeout(LOGIN_SELECTOR_TIMEOUT.toMillis())
        );
        Locator passwordField = findFirstVisible(page, PASSWORD_FIELD_SELECTOR);
        if (passwordField == null) {
            throw new IllegalStateException("Amazon password field not visible");
        }
        passwordField.fill(props.getAmazonPassword());

        Locator signInButton = findFirstVisible(page, SIGN_IN_SELECTORS);
        if (signInButton == null) {
            throw new IllegalStateException("Amazon sign-in submit control not found");
        }
        signInButton.click();
        waitForDomReady(page);

        logPasskeyPromptIfPresent(page);
        detectCaptchaOrThrow(page);
        handleTwoFactorIfNeeded(page);
        detectCaptchaOrThrow(page);
    }

    private void handleTwoFactorIfNeeded(Page page) {
        Optional.ofNullable(findFirstVisible(page, OTP_SELECTORS)).ifPresent(locator -> {
            String otp = System.getenv("AMZN_OTP");
            if (StringUtils.hasText(otp)) {
                log.info("event=OTP_REQUIRED waiting_for_user_input=false");
                locator.fill(otp.trim());
                Locator submit = findFirstVisible(page, MFA_SUBMIT_SELECTORS);
                if (submit != null) {
                    submit.click();
                } else {
                    locator.press("Enter");
                }
                waitForDomReady(page);
            } else {
                log.info("event=OTP_REQUIRED waiting_for_user_input=true");
                try {
                    page.waitForSelector(
                            "input#auth-mfa-otpcode:disabled, input[name='otpCode']:disabled",
                            new Page.WaitForSelectorOptions().setTimeout(OTP_WAIT_TIMEOUT.toMillis())
                    );
                } catch (PlaywrightException e) {
                    log.debug("OTP wait timeout message={}", e.getMessage());
                }
            }
        });
    }

    private Locator findFirstVisible(Page page, List<String> selectors) {
        for (String selector : selectors) {
            Locator visible = findFirstVisible(page, selector);
            if (visible != null) {
                return visible;
            }
        }
        return null;
    }

    private Locator findFirstVisible(Page page, String selector) {
        Locator candidates = page.locator(selector);
        int count = safeCount(candidates);
        for (int i = 0; i < count; i++) {
            Locator candidate = candidates.nth(i);
            if (isLocatorVisible(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private int safeCount(Locator locator) {
        try {
            return locator.count();
        } catch (PlaywrightException e) {
            log.debug("Failed to count locator selector={} message={}", locator, e.getMessage());
            return 0;
        }
    }

    private boolean isLocatorVisible(Locator locator) {
        try {
            return locator.isVisible();
        } catch (PlaywrightException e) {
            log.debug("Visibility check failed message={}", e.getMessage());
            return false;
        }
    }

    private LocatorSelection waitForLoginLink(Page page) {
        Locator composite = page.locator(String.join(", ", LOGIN_LINK_SELECTORS)).first();
        try {
            composite.waitFor(new Locator.WaitForOptions().setTimeout(LOGIN_LINK_TIMEOUT.toMillis()));
        } catch (PlaywrightException e) {
            log.info("event=LOGIN_LINK_TIMEOUT timeoutMs={} message={}",
                    LOGIN_LINK_TIMEOUT.toMillis(), e.getMessage());
            throw new IllegalStateException("Timed out waiting for Amazon login link", e);
        }
        for (String selector : LOGIN_LINK_SELECTORS) {
            Locator candidate = page.locator(selector).first();
            if (safeCount(candidate) == 0) {
                continue;
            }
            if (isLocatorVisible(candidate)) {
                log.info("event=LOGIN_LINK_FOUND method={}", selector);
                return new LocatorSelection(candidate, selector);
            }
        }
        log.info("event=LOGIN_LINK_FOUND method={} visible=false fallback=true", LOGIN_LINK_SELECTORS.get(0));
        return new LocatorSelection(composite, LOGIN_LINK_SELECTORS.get(0));
    }

    private void detectCaptchaOrThrow(Page page) {
        Locator captcha = findFirstVisible(page, CAPTCHA_SELECTORS);
        if (captcha != null) {
            log.warn("event=CAPTCHA_DETECTED message=Captcha encountered; manual intervention required");
            throw new RuntimeException("Captcha encountered during login");
        }
    }

    private void logPasskeyPromptIfPresent(Page page) {
        Locator passkey = findFirstVisible(page, PASSKEY_SELECTORS);
        if (passkey != null) {
            log.info("event=PASSKEY_PROMPT_DETECTED selector={}", passkey.toString());
        }
    }

    private static final class LocatorSelection {
        private final Locator locator;
        private final String selector;

        private LocatorSelection(Locator locator, String selector) {
            this.locator = locator;
            this.selector = selector;
        }
    }

    private void persistStorageState(Path storagePath) {
        try {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(storagePath));
            log.info("event=STORAGESTATE_SAVED path={}", storagePath);
        } catch (PlaywrightException e) {
            throw new IllegalStateException("Failed to persist storage state", e);
        }
    }

    private void waitForDomReady(Page page) {
        if (page == null) {
            return;
        }
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(90_000));
        } catch (PlaywrightException e) {
            log.debug("event=DOM_READY_WAIT_TIMEOUT message={}", e.getMessage());
        }
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeQuietly(Object resource) {
        if (resource == null) {
            return;
        }
        try {
            if (resource instanceof Page p) {
                p.close();
            } else if (resource instanceof BrowserContext c) {
                c.close();
            } else if (resource instanceof Browser b) {
                b.close();
            } else if (resource instanceof Playwright pw) {
                pw.close();
            }
        } catch (Exception ignored) {
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "-";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}



