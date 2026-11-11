package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

final class MenuSnow {
    private MenuSnow() {}

    private static float level;
    private static long last;
    private static float clock;
    private static long checkedAt;
    private static boolean december;

    private static boolean season() {
        int mode = UiSettings.SEASON.index();
        if (mode == 2) return false;
        if (mode == 1) return true;
        long now = System.currentTimeMillis();
        if (now - checkedAt > 30_000L) {
            checkedAt = now;
            december = java.time.LocalDate.now().getMonthValue() == 12;
        }
        return december;
    }

    static float level() {
        return level;
    }

    static void update() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float want = season() ? 1f : 0f;
        level += (want - level) * (1f - (float) Math.exp(-dt * 1.2f));
        if (Math.abs(want - level) < 0.004f) level = want;
        clock += dt;
    }

    static void draw(GuiGraphics g, int width, int height, float storm) {
        if (level <= 0.004f || UiSettings.CALM.on() || Perf.fast()) return;
        int gs = Tex.guiScale();
        String[] names = {"far", "mid", "near"};
        float[] side = {0.70f, 0.95f, 1.30f};
        float[] fall = {0.075f, 0.12f, 0.19f};
        float[] alpha = {0.85f, 0.9f, 0.8f};
        float wind = 0.012f + 0.30f * storm;
        float boost = 1f + 2.0f * storm;
        for (int i = 0; i < 3; i++) {
            int ts = Math.max(64, Math.round(height * side[i]));
            int dev = Math.max(64, Math.min(768, ts * gs));
            Tex.Img img = Tex.fitted("snow_" + names[i], "textures/gui/menu/snow_" + names[i] + ".png", dev, dev, false, 0, 0);
            if (img == null) continue;
            float sway = (float) Math.sin(clock * (0.45f + 0.15f * i) + i * 2.1f) * 0.035f * height * (1f - 0.6f * storm);
            float ox = ((-clock * wind * (0.6f + 0.4f * i) * height + sway) % ts + ts) % ts;
            float oy = ((clock * fall[i] * boost * height) % ts + ts) % ts;
            int tint = Draw.rgba(0xFFFFFFFF, level * Math.min(1f, alpha[i] * (1f + 0.25f * storm)));
            int x0 = Math.round(ox) - ts;
            int y0 = Math.round(oy) - ts;
            for (int x = x0; x < width; x += ts) {
                for (int y = y0; y < height; y += ts) img.draw(g, x, y, ts, ts, tint);
            }
        }
    }

    static void warm(int height, int gs) {
        try {
            String[] names = {"far", "mid", "near"};
            float[] side = {0.70f, 0.95f, 1.30f};
            for (int i = 0; i < 3; i++) {
                int dev = Math.max(64, Math.min(768, Math.round(height * side[i]) * gs));
                Tex.fitted("snow_" + names[i], "textures/gui/menu/snow_" + names[i] + ".png", dev, dev, false, 0, 0);
            }
        } catch (RuntimeException | LinkageError e) {
            com.zlorbie788.zeus.core.Zeus.LOG.warn("Snow pictures could not be prepared", e);
        }
    }
}
