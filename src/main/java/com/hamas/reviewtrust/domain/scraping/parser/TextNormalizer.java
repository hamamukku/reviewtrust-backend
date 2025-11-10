package com.hamas.reviewtrust.domain.scraping.parser;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static Long parseYenToMinor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(value);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseRating(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace(',', '.');
        Double decimalMatch = firstDecimal(normalized);
        if (decimalMatch != null) {
            return decimalMatch;
        }
        String digits = normalized.replaceAll("[^0-9.]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long parseCount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(value);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeDigits(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replace(',', '.');
        String digits = normalized.replaceAll("[^0-9.\\-]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String normalizeDigits(String value) {
        if (value == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char ch = decomposed.charAt(i);
            if (ch == '％') {
                sb.append('%');
                continue;
            }
            if (ch == '，') {
                sb.append(',');
                continue;
            }
            if (ch == '．') {
                sb.append('.');
                continue;
            }
            sb.append(ch);
        }
        return sb.toString().trim();
    }

    private static Double firstDecimal(String text) {
        int idx = text.indexOf('.');
        if (idx <= 0 || idx == text.length() - 1) {
            return null;
        }
        int start = idx - 1;
        while (start >= 0 && Character.isDigit(text.charAt(start))) {
            start--;
        }
        start++;
        int end = idx + 1;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        if (start >= end) {
            return null;
        }
        String number = text.substring(start, end);
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

