package com.hamas.reviewtrust.common.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing simple hashes of text content.
 *
 * <p>This class exposes both a fast 64‑bit FNV‑1a hash for approximate hashing and
 * a cryptographically strong SHA‑256 hash for deduplication or integrity checks.
 * The FNV implementation is convenient for hash‑based partitioning or cache keys
 * where collisions are acceptable. The SHA‑256 method returns a hex string
 * representation suitable for storage or debugging.</p>
 */
public final class TextHash {

    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;

    private TextHash() {
        // utility class
    }

    /**
     * Computes a 64‑bit FNV‑1a hash of the given text using UTF‑8 encoding.
     * This hash is non‑cryptographic and collisions are possible, but it is
     * extremely fast and stable across JVM invocations.
     *
     * @param input text to hash; {@code null} inputs produce 0
     * @return 64‑bit FNV‑1a hash
     */
    public static long fnv64(String input) {
        if (input == null) {
            return 0L;
        }
        long hash = FNV64_OFFSET_BASIS;
        byte[] data = input.getBytes(StandardCharsets.UTF_8);
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= FNV64_PRIME;
        }
        return hash;
    }

    /**
     * Computes the SHA‑256 digest of the given text and returns a lower‑case
     * hexadecimal string. This method is slower than {@link #fnv64(String)} but
     * produces a cryptographically strong hash that is extremely unlikely to
     * collide for distinct inputs.
     *
     * @param input text to hash; {@code null} inputs return an empty string
     * @return hex encoded SHA‑256 digest
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int v = b & 0xff;
                if (v < 0x10) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Should never happen – SHA-256 is guaranteed to exist
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}