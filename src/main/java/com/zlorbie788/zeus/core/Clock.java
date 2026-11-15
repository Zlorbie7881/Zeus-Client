package com.zlorbie788.zeus.core;

/**
 * The time the UI animates by. Normally the system clock; the screenshot test can take it over and step it by
 * exactly one video frame per picture, so filmed menus and knife tricks come out at an even 30 frames a second
 * however slow the test machine renders.
 */
public final class Clock {
    private Clock() {
    }

    /** Test only: when not negative, the animation time in nanoseconds, stepped by the test. */
    public static volatile long manual = -1L;
    private static final long OFFSET_MS = System.currentTimeMillis() - System.nanoTime() / 1_000_000L;

    public static long nanoTime() {
        long m = manual;
        return m >= 0 ? m : System.nanoTime();
    }

    public static long millis() {
        long m = manual;
        return m >= 0 ? m / 1_000_000L + OFFSET_MS : System.currentTimeMillis();
    }
}
