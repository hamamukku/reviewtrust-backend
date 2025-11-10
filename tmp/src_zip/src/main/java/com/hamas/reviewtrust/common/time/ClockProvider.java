package com.hamas.reviewtrust.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Supplies a shared {@link Clock} instance to allow deterministic time
 * handling throughout the application. Tests can override the clock to
 * simulate specific times by calling {@link #setClock(Clock)}.
 */
public final class ClockProvider {
    private static volatile Clock clock = Clock.systemUTC();

    private ClockProvider() {
        // utility class
    }

    /**
     * Returns the current clock. Defaults to {@link Clock#systemUTC()}.
     *
     * @return current clock
     */
    public static Clock getClock() {
        return clock;
    }

    /**
     * Overrides the current clock. This is primarily intended for use in
     * testing to freeze time or simulate specific instants.
     *
     * @param newClock replacement clock
     */
    public static void setClock(Clock newClock) {
        if (newClock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        clock = newClock;
    }

    /**
     * Returns the current instant using the configured clock.
     *
     * @return current instant
     */
    public static Instant now() {
        return clock.instant();
    }

    /**
     * Creates a fixed clock at the specified instant and zone. Useful for
     * tests that require deterministic timestamps.
     *
     * @param instant fixed instant
     * @param zone time zone
     * @return a fixed clock
     */
    public static Clock fixed(Instant instant, ZoneId zone) {
        return Clock.fixed(instant, zone);
    }
}