package com.hamas.reviewtrust.domain.scraping.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;

/**
 * スクレイピング後の文字列フィルタ集。
 * - normalizeText: 正規化（NFKC）/ 改行・空白整形 / 不可視文字除去 / trim（空なら null）
 * - isEmpty: 正規化後に空かどうか
 * - validate: 必須/長さ制限のバリデーション（超過は安全に切り詰め）
 * - sha256Hex: 指紋化（差分アップサート用）
 *
 * MVP要件の「最新N件のみ取得・差分アップサート」実装の下支えとして使用します。:contentReference[oaicite:0]{index=0}
 */
public final class ScrapeFilters {
    private ScrapeFilters() {}

    /** 文字列正規化（空なら null） */
    public static String normalizeText(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\u200B-\\u200F\\uFEFF]", "")   // ゼロ幅系
                .replaceAll("[ \\t\\x0B\\f]+", " ")            // 連続空白→1つ
                .replaceAll("\\s*\\n\\s*", "\n")               // 行頭末の空白除去
                .trim();
        return n.isBlank() ? null : n;
    }

    /** 正規化後に空かどうか */
    public static boolean isEmpty(String s) {
        return normalizeText(s) == null;
    }

    /**
     * 必須/長さ制限バリデーション。
     * @param s 入力
     * @param maxLen 最大長（0以下なら無制限）
     * @param required 必須かどうか
     * @return 正規化後文字列（空はnull, 必須かつ空なら例外）
     */
    public static String validate(String s, int maxLen, boolean required) {
        String n = normalizeText(s);
        if (n == null) {
            if (required) throw new IllegalArgumentException("required text missing");
            return null;
        }
        if (maxLen > 0 && n.length() > maxLen) {
            n = n.substring(0, maxLen).trim();
        }
        return n;
    }

    /** SHA-256（16進） */
    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 error", e);
        }
    }
}
