package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.mods.Brain;

/**
 * Decides whether the decorations (thunder, sway, slide-ins, rain) are drawn at full quality or lighter. In Auto it looks
 * at the frame rate Zeus Brain measures: below 40 frames a second for a few seconds the effects get lighter, and they come
 * back above 55. Quality and Fast force one or the other.
 */
public final class Perf {
    private Perf() {}

    private static boolean light;
    private static long since;

    /** True when decorations should be drawn lighter. */
    public static boolean fast() {
        int mode = UiSettings.PERFORMANCE.index();
        if (mode == 1) return false;
        if (mode == 2) return true;
        long now = System.currentTimeMillis();
        if (now - since < 500) return light;
        since = now;
        int fps = Brain.ctx().fps;
        if (!light && fps > 0 && fps < 40) light = true;
        else if (light && fps >= 55) light = false;
        return light;
    }

    private static int level;
    private static int lowSamples;
    private static int highSamples;
    private static long refreshedAt;

    /**
     * How light the game side of Zeus runs right now: 0 Balanced, 1 Light, 2 Minimal. It is the mode you picked, or higher
     * while the frame rate is low and "Lighten automatically" is on.
     */
    public static int level() {
        return level;
    }

    /** True when ESP lines and boxes should be drawn with fewer pieces (no halos). */
    public static boolean gameFast() {
        return level >= 1;
    }

    /** Scanners run on every n-th tick: 1 in Balanced, 2 in Light, 4 in Minimal. */
    public static int stride() {
        return level == 0 ? 1 : level == 1 ? 2 : 4;
    }

    /** Called once a tick: works out the level from the chosen mode and the measured frame rate. */
    public static void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < 500) return;
        refreshedAt = now;
        int base = Math.max(0, Math.min(2, UiSettings.GAME_MODE.index()));
        int want = base;
        if (UiSettings.AUTO_LIGHTEN.on()) {
            int fps = Brain.ctx().fps;
            if (fps > 0) {
                int needed = fps < 30 ? 2 : fps < 45 ? 1 : 0;
                if (needed > level) {
                    if (++lowSamples >= 2) {
                        level = needed;
                        lowSamples = 0;
                    }
                    highSamples = 0;
                } else if (fps >= 60 && level > base) {
                    if (++highSamples >= 6) {
                        level--;
                        highSamples = 0;
                    }
                    lowSamples = 0;
                } else {
                    lowSamples = 0;
                    highSamples = 0;
                }
                want = Math.max(base, level);
            }
        }
        level = Math.max(base, Math.min(2, want));
    }

    /** How many of the optional bolts to draw: 1 for everything, less when the game struggles. */
    public static float density() {
        return fast() ? 0.35f : 1f;
    }
}
