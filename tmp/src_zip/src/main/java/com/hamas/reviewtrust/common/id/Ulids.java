package com.hamas.reviewtrust.common.id;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Generator for Universally Unique Lexicographically Sortable Identifiers (ULIDs).
 *
 * <p>A ULID is a 128‑bit value consisting of a 48‑bit millisecond timestamp and
 * 80 bits of randomness. When encoded using Crockford's base32 alphabet it
 * results in a 26 character string that maintains lexical ordering by
 * timestamp. This implementation generates ULIDs using the current system
 * clock and a {@link SecureRandom} instance.</p>
 */
public final class Ulids {
    private static final char[] CROCKFORD_BASE32 =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulids() {
        // utility class
    }

    /**
     * Generates a new ULID string using the current time.
     *
     * @return a 26‑character ULID
     */
    public static String generate() {
        return generate(System.currentTimeMillis());
    }

    /**
     * Generates a ULID for the given epoch millisecond timestamp. The random
     * portion is still produced via a secure random source. This can be
     * useful for reproducing ULIDs in tests where determinism of the time
     * component is required.
     *
     * @param timestampMillis epoch milliseconds
     * @return a 26‑character ULID
     */
    public static String generate(long timestampMillis) {
        // allocate 16 bytes: 6 for timestamp (most significant) and 10 for randomness
        byte[] data = new byte[16];
        // encode timestamp into first 6 bytes big‑endian
        for (int i = 5; i >= 0; i--) {
            data[i] = (byte) (timestampMillis & 0xFF);
            timestampMillis >>>= 8;
        }
        // random component
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);
        System.arraycopy(rnd, 0, data, 6, rnd.length);

        // convert 128‑bit value to base32 string padded to 26 chars
        BigInteger value = new BigInteger(1, data);
        char[] chars = new char[26];
        BigInteger base = BigInteger.valueOf(32);
        for (int i = 25; i >= 0; i--) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            int idx = divRem[1].intValue();
            chars[i] = CROCKFORD_BASE32[idx];
            value = divRem[0];
        }
        return new String(chars);
    }

    /**
     * Extracts the epoch millisecond timestamp from the given ULID string.
     * Only the first 10 characters (50 bits) contain time information; the
     * remaining characters are random. This method returns the original
     * 48‑bit timestamp value, discarding the two unused high bits.
     *
     * @param ulid a valid ULID string
     * @return epoch milliseconds encoded in the ULID
     * @throws IllegalArgumentException if the string is not 26 characters or contains invalid chars
     */
    public static long extractTime(String ulid) {
        if (ulid == null || ulid.length() != 26) {
            throw new IllegalArgumentException("ULID must be 26 chars: " + ulid);
        }
        long time = 0L;
        for (int i = 0; i < 10; i++) {
            char c = ulid.charAt(i);
            int idx = indexOfBase32(c);
            time = (time << 5) | idx;
        }
        // the time component occupies only the lower 48 bits of these 50 bits
        return time & 0x0000ffffffffffffL;
    }

    private static int indexOfBase32(char c) {
        // map base32 char to 0-31 value
        // since the alphabet omits certain letters, do a linear scan
        for (int i = 0; i < CROCKFORD_BASE32.length; i++) {
            if (CROCKFORD_BASE32[i] == c) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid ULID character: " + c);
    }
}