package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small thunders used all over the interface: crawling along the edge of a button or card, circling a logo, leaping off a
 * switch. They are drawn fresh every frame from a time slot, so each one lives a fraction of a second and then jumps elsewhere.
 */
public final class Thunder {
    private Thunder() {}

    private static final int BLUE = 0xFF5C9CFF;
    private static final int DEEP = 0xFF2A5CFF;

    /** Small jagged bolts that flicker around a circle. */
    static void ring(GuiGraphics g, float cx, float cy, float r, float t, float alpha, float k) {
        if (UiSettings.CALM.on() || Perf.fast()) return;
        if (Anim.menuReady()) {
            Anim.circ(g, cx, cy, r, t, BLUE, 0xFFDCEBFF, Math.min(1f, alpha));
            return;
        }
        for (int j = 0; j < 7; j++) {
            int slot = (int) Math.floor(t * 8f + j * 0.41f);
            if (Lightning.hash(slot, j * 13 + 5) < 0.35f) continue;
            float a0 = Lightning.hash(slot, j * 7 + 1) * 6.2832f;
            float da = 0.35f + 0.6f * Lightning.hash(slot, j * 7 + 2);
            float x1 = cx + (float) Math.cos(a0) * r;
            float y1 = cy + (float) Math.sin(a0) * r;
            float x2 = cx + (float) Math.cos(a0 + da) * r * 1.18f;
            float y2 = cy + (float) Math.sin(a0 + da) * r * 1.18f;
            Lightning.bolt(g, x1, y1, x2, y2, 1f, slot * 31 + j, alpha, BLUE, DEEP, Math.max(0.3f, 0.38f * k), 1, 0.5f, 0f, 3);
        }
    }

    /** Where a point {@code p} along the outline of a rectangle is, and which way is outward there. */
    private static float[] onOutline(float x, float y, float w, float h, float p) {
        float per = 2f * (w + h);
        p = ((p % per) + per) % per;
        if (p < w) return new float[] {x + p, y, 0f, -1f};
        p -= w;
        if (p < h) return new float[] {x + w, y + p, 1f, 0f};
        p -= h;
        if (p < w) return new float[] {x + w - p, y + h, 0f, 1f};
        p -= w;
        return new float[] {x, y + h - p, -1f, 0f};
    }

    /**
     * An energy arc that flows along the top and the bottom edge of a rectangle, with a soft blue edge so the shape always reads.
     * The animation was rendered offline and is only played here ({@link Anim}). {@code strength} is 0 to 1.
     */
    static void outline(GuiGraphics g, int x, int y, int w, int h, float t, float strength, float k, float density) {
        if (strength <= 0.01f) return;
        int edge = Draw.rgba(0xFF8EB8FF, 0.30f * strength);
        Draw.outline(g, x - 1, y - 1, x + w + 1, y + h + 1, edge);
        if (UiSettings.CALM.on() || Perf.fast() || w < 24 || h < 12) return;
        int ah = Math.max(10, Math.round(Math.min(h * 0.62f, 34f * Math.max(1f, k))));
        int inset = Math.max(4, Math.round(h * 0.18f));
        int aw = Math.max(8, w - 2 * inset);
        Anim.arc(g, x + inset, y, aw, ah, t, 0f, BLUE, 0xFFDCEBFF, strength);
        Anim.arc(g, x + inset, y + h, aw, ah, t, 0.45f, BLUE, 0xFFDCEBFF, strength * 0.9f);
    }

    /** A burst of bolts from a point, for when something switches on. {@code p} runs 0 to 1 over the burst. */
    static void burst(GuiGraphics g, float cx, float cy, float reach, float p, float k) {
        if (p <= 0f || p >= 1f || UiSettings.CALM.on()) return;
        Anim.burst(g, cx, cy, Math.round(reach * 2.4f), p, BLUE, 0xFFE6F1FF, 1f);
    }
}
