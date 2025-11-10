package com.hamas.reviewtrust.scraping;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.time.Duration;

/**
 * Encapsulates the selectors and interactions needed to progress through Amazon's login flow.
 */
public final class AmazonLoginSelectors {

    private static final String[] EMAIL_INPUT_SELECTORS = {
            "#ap_email_login",
            "input#ap_email",
            "input[name='email']",
            "input[type='email']",
            "input[autocomplete='username']",
            "input[aria-label*='email']"
    };

    private static final String[] CONTINUE_BUTTON_SELECTORS = {
            "#continue",
            "#continue input[type='submit']",
            "input#continue",
            "input[type='submit'][aria-labelledby='continue-announce']"
    };

    private static final String[] PASSWORD_INPUT_SELECTORS = {
            "input#ap_password:not(.aok-hidden)",
            "input[name='password']:not(.aok-hidden)",
            "input[type='password']:not(.aok-hidden)"
    };

    private static final String[] SIGN_IN_BUTTON_SELECTORS = {
            "#signInSubmit",
            "input#signInSubmit",
            "form[action*='/ap/signin'] input[type='submit']"
    };

    private static final String[] OTP_INPUT_SELECTORS = {
            "#auth-mfa-otpcode",
            "input#cvf-input-code",
            "input[name='otpCode']",
            "input[name='code']"
    };

    private static final String[] CAPTCHA_SELECTORS = {
            "iframe[src*='captcha']",
            "img[src*='captcha']",
            "input[name='cvf_captcha_input']"
    };

    private static final String[] CHALLENGE_URL_TOKENS = {
            "/ap/cvf",
            "/ap/challenge",
            "/challenge/",
            "captcha",
            "/cvf/handle",
            "/ap/regchallenge",
            "aiv-auth-mfa"
    };

    /**
     * Fill the email/username field.
     */
    public void fillEmail(Page page, String email) {
        Locator field = waitForAnySelector(page, EMAIL_INPUT_SELECTORS, 30_000);
        if (field == null) {
            throw new IllegalStateException("Unable to locate Amazon email input field.");
        }
        field.fill(email);
        field.blur();
        throwIfErrorVisible(page, "#invalid-email-alert, #empty-claim-alert");
    }

    /**
     * Click the continue button on the first step of the login form.
     */
    public void clickContinue(Page page) {
        Locator button = waitForAnySelector(page, CONTINUE_BUTTON_SELECTORS, 10_000);
        if (button == null) {
            return;
        }
        try {
            page.waitForNavigation(button::click);
        } catch (PlaywrightException e) {
            button.click();
        }
    }

    /**
     * Fill the password field.
     */
    public void fillPassword(Page page, String password) {
        Locator field = waitForAnySelector(page, PASSWORD_INPUT_SELECTORS, 30_000);
        if (field == null) {
            throw new IllegalStateException("Unable to locate Amazon password input field.");
        }
        field.fill(password);
        field.blur();
        throwIfErrorVisible(page, "#invalid-password-alert");
    }

    /**
     * Submit the sign-in form either by clicking the button or pressing Enter.
     */
    public void submitSignin(Page page) {
        Locator button = waitForAnySelector(page, SIGN_IN_BUTTON_SELECTORS, 10_000);
        if (button != null) {
            try {
                page.waitForNavigation(button::click);
            } catch (PlaywrightException e) {
                button.click();
            }
        } else {
            page.keyboard().press("Enter");
        }
    }

    /**
     * Wait for potential CAPTCHA / MFA steps to be resolved manually before continuing.
     */
    public void handleChallengesIfPresent(Page page, Duration budget) {
        if (page == null || budget == null || budget.isZero() || budget.isNegative()) {
            return;
        }

        long deadline = System.nanoTime() + budget.toNanos();
        boolean notified = false;

        while (System.nanoTime() < deadline) {
            String currentUrl = safeUrl(page);
            if (currentUrl != null && isLoginComplete(currentUrl)) {
                System.out.println("[AmazonLoginSelectors] login completed at " + currentUrl);
                return;
            }

            if (!notified && currentUrl != null && isChallengeUrl(currentUrl)) {
                System.out.println("[AmazonLoginSelectors] challenge detected; waiting for manual completion (" + currentUrl + ")");
                notified = true;
            }

            if (!notified && findExistingSelector(page, OTP_INPUT_SELECTORS) != null) {
                System.out.println("[AmazonLoginSelectors] OTP input visible; waiting for verification.");
                notified = true;
            }

            if (!notified && findExistingSelector(page, CAPTCHA_SELECTORS) != null) {
                System.out.println("[AmazonLoginSelectors] CAPTCHA visible; waiting for resolution.");
                notified = true;
            }

            page.waitForTimeout(1_000);
        }
    }

    /**
     * Wait for any selector in the provided array to become visible on the page.
     */
    public Locator waitForAnySelector(Page page, String[] selectors, double timeoutMillis) {
        if (page == null || selectors == null || selectors.length == 0) {
            return null;
        }

        long timeout = timeoutMillis <= 0 ? 0L : (long) timeoutMillis;
        long perSelectorTimeout = selectors.length == 0 ? timeout : Math.max(500L, timeout / selectors.length);

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector);
                if (timeout > 0) {
                    locator.waitFor(new WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(perSelectorTimeout));
                }
                Locator candidate = locator.first();
                if (candidate != null && candidate.isVisible()) {
                    return candidate;
                }
            } catch (PlaywrightException ignored) {
                // try next selector
            }
        }

        return findExistingSelector(page, selectors);
    }

    /**
     * Find a selector that is currently present and visible without waiting.
     */
    public Locator findExistingSelector(Page page, String[] selectors) {
        if (page == null || selectors == null) {
            return null;
        }
        for (String selector : selectors) {
            try {
                Locator query = page.locator(selector);
                if (query.count() > 0) {
                    Locator candidate = query.first();
                    if (candidate.isVisible()) {
                        return candidate;
                    }
                }
            } catch (PlaywrightException ignored) {
                // ignore and try next selector
            }
        }
        return null;
    }

    private void throwIfErrorVisible(Page page, String selector) {
        try {
            Locator alert = page.locator(selector).first();
            if (alert.count() > 0 && alert.isVisible()) {
                String message;
                try {
                    message = alert.innerText().trim();
                } catch (PlaywrightException ex) {
                    message = selector;
                }
                throw new IllegalStateException("Amazon reported an error: " + message);
            }
        } catch (PlaywrightException ignored) {
            // ignore and proceed
        }
    }

    private boolean isChallengeUrl(String url) {
        String lower = url.toLowerCase();
        for (String token : CHALLENGE_URL_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoginComplete(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        return !lower.contains("/ap/signin") && !isChallengeUrl(lower);
    }

    private String safeUrl(Page page) {
        try {
            return page.url();
        } catch (PlaywrightException e) {
            return null;
        }
    }
}
