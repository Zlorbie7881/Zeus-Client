package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * What happens on the Zeus menu's buttons when the pointer reaches them, besides the lightning of {@link PlateFx}.
 *
 * <p><b>Stone plates</b>: a spring that lifts the plate with a small overshoot, a pale shine that sweeps across the plate
 * once as the pointer arrives (the letters flash white as it passes over them), a ripple that spreads from the point where
 * the pointer came in, embers that rise off the plate while it is lit, and a streak of light along its lower edge.
 *
 * <p><b>Round buttons</b>: a spring that makes the button jump up and settle, a ring that leaves it when the pointer
 * arrives, slower rings that keep leaving it, and a spark that circles its rim.
 *
 * <p>Everything is a function of time and the few numbers it is given, so no state is kept here.
 */
final class HoverFx {
    private HoverFx() {}

    private static final int ICE = 0xFFDCE8FF;
    private static final int BLUE = 0xFF7CA4FF;

    /** One step of a spring that is pulled towards {@code target}: returns the new position, velocity goes back in {@code vel[idx]}. */
    static float spring(float pos, float[] vel, int idx, float target, float dt) {
        float h = Math.min(dt, 0.033f);
        // two half steps keep it stable when a frame is slow
        for (int k = 0; k < 2; k++) {
            vel[idx] += ((target - pos) * 230f - vel[idx] * 15f) * (h / 2f);
            pos += vel[idx] * (h / 2f);
        }
        if (Math.abs(target - pos) < 0.0008f && Math.abs(vel[idx]) < 0.01f) {
            vel[idx] = 0f;
            return target;
        }
        return pos;
    }

    // ---------------------------------------------------------------- plates

    /**
     * The shine: a slanted pale band that crosses the plate once. {@code p} is 0 to 1 (how far it has gone). Returns the left
     * and right edge of the band in {@code out} (so the letters can be lit inside it), or false when it is not on the plate.
     */
    static boolean shine(GuiGraphics g, int x, int y, int w, int h, float p, float u, float strength, int[] out) {
        if (p <= 0f || p >= 1f || strength <= 0.01f) return false;
        float e = p * p * (3f - 2f * p);
        float half = Math.max(6f, h * 0.60f);
        float slant = 0.75f;
        float cx = x - half - h * slant + e * (w + 2f * half + h * slant * 2f);
        float fade = (float) Math.sin(Math.PI * Math.min(1f, p * 1.05f));
        g.enableScissor(x, y, x + w, y + h);
        for (int row = 0; row < h; row++) {
            float c = cx + (row - h / 2f) * slant;
            int cy = y + row;
            band(g, c - half, c + half, cy, Draw.rgba(ICE, 0.11f * fade * strength));
            band(g, c - half * 0.64f, c + half * 0.64f, cy, Draw.rgba(ICE, 0.16f * fade * strength));
            band(g, c - half * 0.28f, c + half * 0.28f, cy, Draw.rgba(0xFFFFFFFF, 0.28f * fade * strength));
        }
        g.disableScissor();
        out[0] = Math.round(cx - half * 0.7f - h * slant * 0.5f);
        out[1] = Math.round(cx + half * 0.7f + h * slant * 0.5f);
        return true;
    }

    private static void band(GuiGraphics g, float x0, float x1, int y, int color) {
        g.fill(Math.round(x0), y, Math.round(x1), y + 1, color);
    }

    /** A ring that spreads from the point where the pointer came in; {@code p} is 0 to 1. */
    static void ripple(GuiGraphics g, int x, int y, int w, int h, float entryX, float p, float u, float strength) {
        if (p <= 0f || p >= 1f || strength <= 0.01f) return;
        float e = 1f - (1f - p) * (1f - p);
        float rx = e * w * 0.85f + 2f;
        float ry = rx * 0.34f;
        float a = (1f - p) * (1f - p) * 0.60f * strength;
        float cx = Math.max(x, Math.min(x + w, entryX));
        float cy = y + h / 2f;
        g.enableScissor(x, y, x + w, y + h);
        ring(g, cx, cy, rx, ry, Math.max(1f, 1.6f * u * (1f - p) + 0.6f), Draw.rgba(0xFFBCD2FF, a), 40);
        ring(g, cx, cy, rx * 0.72f, ry * 0.72f, 1f, Draw.rgba(0xFFFFFFFF, a * 0.55f), 32);
        g.disableScissor();
    }

    /** Embers that rise off the plate. */
    static void embers(GuiGraphics g, int x, int y, int w, int h, float t, float s, float u, int seed) {
        if (s <= 0.05f) return;
        int n = Perf.fast() ? 5 : 11;
        for (int k = 0; k < n; k++) {
            float period = 0.9f + 0.7f * Lightning.hash(k, 3 + seed);
            float ph = t / period + Lightning.hash(k, 9 + seed);
            int id = (int) Math.floor(ph);
            float age = ph - id;
            float px = x + w * (0.04f + 0.92f * Lightning.hash(id * 31 + k, 5 + seed));
            float rise = (10f + 20f * Lightning.hash(id * 17 + k, 7 + seed)) * u;
            float py = y + h * 0.18f - age * rise;
            float sway = (float) Math.sin(age * 5.5f + k * 2.3f) * 2.2f * u;
            float a = (float) Math.pow(Math.sin(Math.PI * age), 1.2) * s * 0.95f;
            if (a <= 0.03f) continue;
            int ix = Math.round(px + sway);
            int iy = Math.round(py);
            int sz = u > 1.7f ? 2 : 1;
            g.fill(ix, iy, ix + sz, iy + sz, Draw.rgba(0xFFFFFFFF, a));
            if (age < 0.7f) g.fill(ix - 1, iy - 1, ix + sz + 1, iy + sz + 1, Draw.rgba(BLUE, a * 0.28f));
        }
    }

    /** A line of light along the lower edge of the plate that grows out of the middle. */
    static void underline(GuiGraphics g, int x, int y, int w, int h, float s, float t) {
        if (s <= 0.03f) return;
        float k = (float) Math.pow(s, 0.6);
        float half = w * 0.5f * k;
        float cx = x + w / 2f;
        int ly = y + h;
        float breathe = 0.85f + 0.15f * (float) Math.sin(t * 5.0f);
        for (int i = 0; i < 3; i++) {
            float f = 1f - i / 3f;
            int l = Math.round(half * f);
            g.fill(Math.round(cx) - l, ly - 1, Math.round(cx) + l, ly, Draw.rgba(i == 2 ? 0xFFFFFFFF : 0xFFA8C4FF, 0.30f * s * breathe + 0.15f * i * s));
        }
        Lightning.streak(g, cx, ly - 0.5f, half * 0.9f, 0xFF9CBCFF, s * 0.9f);
        Lightning.bloom(g, cx, ly, Math.max(8f, half * 0.55f), 0xFF7C9CE0, 0.20f * s);
    }

    // ---------------------------------------------------------------- round buttons

    /**
     * Rings that leave a round button: one big one when the pointer arrives ({@code since} is the time since, in seconds), then a
     * slow soft one every second or so while it stays; and a spark that runs round the rim.
     */
    static void roundFx(GuiGraphics g, float cx, float cy, float r, float t, float since, float s, float u) {
        if (s <= 0.04f) return;
        float p0 = since / 0.65f;
        if (p0 > 0f && p0 < 1f) {
            float e = 1f - (1f - p0) * (1f - p0);
            ring(g, cx, cy, r * (1f + 0.85f * e), r * (1f + 0.85f * e), Math.max(1f, 2.2f * u * (1f - p0)), Draw.rgba(0xFFBCD2FF, 0.65f * (1f - p0) * (1f - p0)), 48);
        }
        float ph = ((t * 0.8f) % 1f);
        if (since > 0.65f) {
            ring(g, cx, cy, r * (1.02f + 0.45f * ph), r * (1.02f + 0.45f * ph), 1f, Draw.rgba(0xFF9CBCFF, 0.32f * (1f - ph) * (1f - ph) * s), 40);
        }
        if (!Perf.fast() && !Anim.menuReady()) {
            // the spark circles the rim with a tail behind it
            float ang = t * 5.6f;
            for (int i = 0; i < 9; i++) {
                float a = ang - i * 0.085f;
                float px = cx + (float) Math.cos(a) * r * 1.01f;
                float py = cy + (float) Math.sin(a) * r * 1.01f;
                float f = 1f - i / 9f;
                int ix = Math.round(px);
                int iy = Math.round(py);
                g.fill(ix, iy, ix + 1, iy + 1, Draw.rgba(i == 0 ? 0xFFFFFFFF : 0xFFA8C4FF, f * f * s));
            }
            float hx = cx + (float) Math.cos(ang) * r * 1.01f;
            float hy = cy + (float) Math.sin(ang) * r * 1.01f;
            Lightning.bloom(g, hx, hy, 5f * u + 3f, 0xFFB4C8FF, 0.55f * s);
        }
    }

    /** An outline of an ellipse made of short straight pieces. */
    static void ring(GuiGraphics g, float cx, float cy, float rx, float ry, float thick, int color, int segs) {
        if (Draw.a(color) < 3) return;
        float px = cx + rx;
        float py = cy;
        for (int i = 1; i <= segs; i++) {
            float a = (float) (i * 2.0 * Math.PI / segs);
            float nx = cx + (float) Math.cos(a) * rx;
            float ny = cy + (float) Math.sin(a) * ry;
            Draw.line(g, px, py, nx, ny, thick, color);
            px = nx;
            py = ny;
        }
    }
}
