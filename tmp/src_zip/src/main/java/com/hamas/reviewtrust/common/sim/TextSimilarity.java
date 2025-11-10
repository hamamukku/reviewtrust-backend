package com.hamas.reviewtrust.common.sim;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Functions for computing similarity metrics between two strings.
 *
 * <p>The provided algorithms are simple and have no external dependencies.
 * They are designed for short texts such as review titles or short
 * descriptions. For long documents or natural language comparisons a
 * library such as Apache Lucene or SimHash may be more appropriate.</p>
 */
public final class TextSimilarity {

    private TextSimilarity() {
        // utility class
    }

    /**
     * Computes the Jaccard similarity between two strings based on unique
     * whitespace‑delimited tokens. The similarity is defined as the size
     * of the intersection divided by the size of the union of token sets.
     *
     * @param a first string
     * @param b second string
     * @return similarity in the range [0,1]
     */
    public static double jaccard(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> sa = new HashSet<>(Arrays.asList(a.trim().split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.trim().split("\\s+")));
        if (sa.isEmpty() && sb.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    /**
     * Computes the Levenshtein edit distance between two strings. This
     * implementation uses a dynamic programming algorithm with O(n*m)
     * complexity and is suitable for relatively short inputs.
     *
     * @param a first string
     * @param b second string
     * @return edit distance
     */
    public static int levenshteinDistance(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * Returns a normalized Levenshtein similarity score between two strings.
     * The score is {@code 1 - (distance / maxLength)}, yielding a value of 1
     * for identical strings and 0 for completely different strings.
     *
     * @param a first string
     * @param b second string
     * @return similarity in [0,1]
     */
    public static double normalizedLevenshtein(String a, String b) {
        int dist = levenshteinDistance(a, b);
        int max = Math.max(a != null ? a.length() : 0, b != null ? b.length() : 0);
        if (max == 0) return 1.0;
        return 1.0 - ((double) dist / max);
    }
}