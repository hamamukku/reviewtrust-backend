package com.hamas.reviewtrust.common.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizes textual input by applying Unicode canonicalization, converting
 * fullwidth forms to halfwidth, removing accents and diacritics, lower‑casing
 * and collapsing whitespace. Use this when comparing or hashing free text to
 * reduce spurious differences arising from encoding quirks.
 */
public final class TextNormalizer {
    private TextNormalizer() {
        // utility class
    }

    /**
     * Performs a normalization on the supplied string. The following
     * transformations are applied:
     *
     * <ul>
     *   <li>Null inputs return {@code null}.</li>
     *   <li>Unicode is canonicalized using NFKD then diacritics are removed.</li>
     *   <li>The text is lower‑cased using the ROOT locale.</li>
     *   <li>All runs of whitespace are replaced by a single ASCII space.</li>
     *   <li>Leading and trailing whitespace is trimmed.</li>
     * </ul>
     *
     * @param input text to normalize
     * @return normalized text or {@code null}
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        // Canonical decomposition and compatibility mapping
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        // Remove diacritical marks
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            // skip combining marks (M category)
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        // Lower case
        String lower = sb.toString().toLowerCase(Locale.ROOT);
        // Collapse whitespace
        String collapsed = lower.replaceAll("\\s+", " ").trim();
        return collapsed;
    }
}