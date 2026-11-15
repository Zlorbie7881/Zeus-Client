package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.mods.Brain;

public final class Perf {
    private Perf() {}

    private static boolean light;
    private static long since;

    public static boolean fast() {
        int mode = UiSettings.PERFORMANCE.index();
        if (mode == 1) return false;
        if (mode == 2) return true;
        long now = System.currentTimeMillis();
        if (now - since < 500) return light;
        since = now;
        int fps = Brain.ctx().fps;
        // most effects are cheap now, so only a frame rate that is really struggling turns them down
        if (!light && fps > 0 && fps < 30) light = true;
        else if (light && fps >= 45) light = false;
        return light;
    }

    private static int level;
    private static int lowSamples;
    private static int highSamples;
    private static long refreshedAt;

    public static int level() {
        return level;
    }

    public static boolean gameFast() {
        return level >= 1;
    }

    public static int stride() {
        return level == 0 ? 1 : level == 1 ? 2 : 4;
    }

    public static void refresh() {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < 500) return;
        refreshedAt = now;
        int base = Math.max(0, Math.min(2, UiSettings.GAME_MODE.index()));
        int want = base;
        if (UiSettings.AUTO_LIGHTEN.on()) {
            int fps = Brain.ctx().fps;
            if (fps > 0) {
                int needed = fps < 20 ? 2 : fps < 30 ? 1 : 0;
                if (needed > level) {
                    if (++lowSamples >= 2) {
                        level = needed;
                        lowSamples = 0;
                    }
                    highSamples = 0;
                } else if (fps >= 50 && level > base) {
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

    public static float density() {
        return fast() ? 0.35f : 1f;
    }
}
