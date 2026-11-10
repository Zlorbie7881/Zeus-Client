package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The aurora over the seasonal menu: a green and violet curtain in the sky on clear winter nights. It comes up slowly with the
 * snow (so it follows {@link UiSettings#SEASON}), goes away when a storm builds, and keeps clear of the moon. It is drawn
 * behind the clouds, so the banks of cloud drift in front of it.
 */
final class MenuAurora {
    private MenuAurora() {}

    private static final AuroraSky SKY = new AuroraSky();
    private static final AuroraSky.Style STYLE = new AuroraSky.Style();
    private static float level;
    private static long last;

    static {
        STYLE.curtains = 4;
        STYLE.scheme = 0;
        STYLE.height = 13f;
        STYLE.bearing = 0f;
        STYLE.spread = 54f;
        STYLE.activity = 0.82f;
        STYLE.surges = true;
        STYLE.seed = 11;
    }

    /** How much of the aurora is showing, 0 to 1. */
    static float level() {
        return level;
    }

    /**
     * @param storm the storm level 0 to 1; {@code moonX}, {@code moonY} and {@code moonR} are the moon on the screen, so the
     *              curtains thin out around it
     */
    static void draw(GuiGraphics g, int width, int height, float t, float storm, float moonX, float moonY, float moonR, boolean still) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float want = UiSettings.MENU_AURORA.on() && !Perf.fast() ? MenuSnow.level() * (1f - Math.min(1f, storm * 1.4f)) : 0f;
        level += (want - level) * (1f - (float) Math.exp(-dt * 0.8f));
        if (Math.abs(want - level) < 0.004f) level = want;
        if (level <= 0.01f) return;
        SKY.begin(still ? 47f : t, STYLE);
        final float horizon = height * 0.70f;
        final float yTop = 0f;
        final float mx = moonX;
        final float my = moonY;
        final float mr = Math.max(8f, moonR);
        AuroraSky.View view = new AuroraSky.View() {
            @Override
            public boolean at(float sx, float sy, float[] azel) {
                azel[0] = (sx / width - 0.5f) * 150f;
                azel[1] = 1.5f + (horizon - sy) / horizon * 62f;
                return true;
            }

            @Override
            public float mask(float sx, float sy) {
                float dx = sx - mx;
                float dy = sy - my;
                float d2 = (dx * dx + dy * dy) / (mr * mr * 5.5f);
                float moon = 1f - 0.9f * (float) Math.exp(-d2);
                float low = 1f - Math.max(0f, Math.min(1f, (sy - horizon * 0.80f) / (horizon * 0.22f)));
                return moon * low;
            }
        };
        int col = Math.max(3, Math.round(width / 260f));
        int row = Math.max(6, Math.round(height / 46f));
        SKY.render(g, 0, Math.round(yTop), width, Math.round(horizon), view, 1.3f * level, col, row);
    }
}
