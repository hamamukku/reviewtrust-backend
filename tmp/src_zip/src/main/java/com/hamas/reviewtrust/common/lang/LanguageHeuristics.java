package com.hamas.reviewtrust.common.lang;

import java.util.Locale;

/**
 * Simple heuristics for detecting the primary language of a text snippet.
 *
 * <p>For performance and determinism this detector supports only a very small
 * subset of languages and does not attempt to be comprehensive. It is
 * primarily intended for routing behaviour between Japanese and English
 * processing pipelines. A more robust solution should use a dedicated
 * language detection library.</p>
 */
public final class LanguageHeuristics {

    private LanguageHeuristics() {
        // utility class
    }

    /**
     * Detects whether the given text appears to be primarily Japanese. The
     * heuristic counts Hiragana, Katakana and CJK Unified Ideograph code
     * points and returns true if they constitute at least 30% of the
     * characters (excluding whitespace and punctuation).
     *
     * @param text input text
     * @return true if the text is mostly Japanese
     */
    public static boolean isJapanese(String text) {
        if (text == null || text.isEmpty()) return false;
        int jaCount = 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) continue;
            if (isCJK(c) || isHiragana(c) || isKatakana(c)) {
                jaCount++;
            }
            total++;
        }
        if (total == 0) return false;
        return (double) jaCount / (double) total >= 0.3;
    }

    /**
     * Detects the probable language of the input. Returns a two‑letter ISO 639
     * language code such as "ja" for Japanese, "en" for English or
     * "unknown" if detection is inconclusive. Only Japanese and English are
     * currently distinguished; all other scripts default to "unknown".
     *
     * @param text input text
     * @return language code
     */
    public static String detect(String text) {
        if (text == null || text.isBlank()) return "unknown";
        if (isJapanese(text)) {
            return "ja";
        }
        // crude heuristic: if ASCII ratio is high assume English
        int ascii = 0, letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (c <= 0x007F) ascii++;
            }
        }
        if (letters > 0 && (double) ascii / letters > 0.7) {
            return "en";
        }
        return "unknown";
    }

    private static boolean isHiragana(char c) {
        return c >= 0x3040 && c <= 0x309F;
    }

    private static boolean isKatakana(char c) {
        return c >= 0x30A0 && c <= 0x30FF;
    }

    private static boolean isCJK(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }
}