package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The lightning of the menu's stone plates.
 *
 * <p><b>Hover</b>: two live arcs run round the edge of the plate, one chasing the other. Each is a jagged channel that is
 * redrawn from fresh noise about sixteen times a second (so it crackles), tapers from a white-hot head to a faint tail, and has
 * a halo, a body and a core. The head carries a bloom, a small lens glint and a few sparks, little forks leap off the channel
 * now and then, and the stone under the head is lit by it.
 *
 * <p><b>Click</b>: a flash goes through the whole menu: the plate lights white, a shock wave leaves it, bolts fly out to the
 * edges of the screen and the picture is washed in pale blue light that fades; the next screen opens in the middle of it.
 */
final class PlateFx {
    private PlateFx() {}

    private static final int BLUE = 0xFF5C9CFF;
    private static final int DEEP = 0xFF2A5CFF;
    private static final int ICE = 0xFFCFE2FF;

    /** Smooth value noise in -1..1. */
    private static float vn(float x, int seed) {
        int i = (int) Math.floor(x);
        float f = x - i;
        f = f * f * (3f - 2f * f);
        float a = Lightning.hash(i, seed) * 2f - 1f;
        float b = Lightning.hash(i + 1, seed) * 2f - 1f;
        return a + (b - a) * f;
    }

    /** A point on the rectangle's outline {@code p} pixels from the top left corner, going clockwise; the last two are the outward direction. */
    private static void onOutline(float x, float y, float w, float h, float p, float[] out) {
        float per = 2f * (w + h);
        p = ((p % per) + per) % per;
        if (p < w) {
            out[0] = x + p; out[1] = y; out[2] = 0f; out[3] = -1f;
            return;
        }
        p -= w;
        if (p < h) {
            out[0] = x + w; out[1] = y + p; out[2] = 1f; out[3] = 0f;
            return;
        }
        p -= h;
        if (p < w) {
            out[0] = x + w - p; out[1] = y + h; out[2] = 0f; out[3] = 1f;
            return;
        }
        p -= w;
        out[0] = x; out[1] = y + h - p; out[2] = -1f; out[3] = 0f;
    }

    /** The arcs that run round a plate while the pointer is on it. {@code s} is 0 to 1, how strongly it is lit. */
    static void hover(GuiGraphics g, int x, int y, int w, int h, float t, float s, float u) {
        if (s <= 0.02f) return;
        if (!Perf.fast() && Anim.menuReady()) {
            // rendered offline: two crackling arcs chase each other round the edge (see tools/anim/make_plate.py)
            Draw.rrectLine(g, x - 1, y - 1, w + 2, h + 2, 3, Draw.rgba(0xFF8EB8FF, 0.30f * s));
            Anim.plate(g, x, y, w, h, t, BLUE, ICE, Math.min(1f, s));
            return;
        }
        boolean fast = Perf.fast();
        float per = 2f * (w + h);
        float k = Math.max(0.7f, u);
        float width = Math.max(0.55f, 0.62f * k);
        int heads = fast ? 1 : 2;
        float[] c = new float[4];
        float[] hc = new float[4];
        // the whole edge glows a little, so the shape of the plate always reads
        Draw.rrectLine(g, x - 1, y - 1, w + 2, h + 2, 3, Draw.rgba(0xFF8EB8FF, 0.30f * s));

        for (int hd = 0; hd < heads; hd++) {
            float speed = 0.34f + 0.05f * hd;
            float pos = ((t * speed + hd * 0.5f) % 1f) * per;          // the head, in pixels along the outline
            float trail = per * (0.30f - 0.05f * hd);
            int step = fast ? 6 : 3;                                    // pixels between two points of the channel
            int n = Math.max(6, Math.round(trail / step));
            int slot = (int) Math.floor(t * 16f);
            float jag = 3.4f * k;
            float prevX = 0f;
            float prevY = 0f;
            boolean have = false;
            for (int i = 0; i <= n; i++) {
                float f = i / (float) n;                                 // 0 = tail, 1 = head
                onOutline(x, y, w, h, pos - trail * (1f - f), c);
                // sideways jag: two octaves of noise that slide with time, redrawn from fresh noise every slot
                float along = (pos - trail * (1f - f)) * 0.045f;
                float off = (0.70f * vn(along * 1.0f + slot * 3.1f, 11 + hd) + 0.45f * vn(along * 2.9f + slot * 7.7f, 23 + hd)) * jag;
                float taper = (float) Math.sin(Math.min(1f, f * 1.12f) * 1.5708f);   // thin tail, full body
                off *= 0.35f + 0.65f * taper;
                float px = c[0] + c[2] * off;
                float py = c[1] + c[3] * off;
                if (have) {
                    float a = s * (float) Math.pow(f, 1.35);
                    Lightning.seg(g, prevX, prevY, px, py, Math.min(1f, a), BLUE, DEEP, width * (0.55f + 0.45f * f), fast ? 2 : 3);
                }
                prevX = px;
                prevY = py;
                have = true;
                if (i == n) {
                    hc[0] = px; hc[1] = py; hc[2] = c[2]; hc[3] = c[3];
                }
                // forks leaping off the channel, outward, a few times a second
                if (!fast && i > n / 3 && i % 5 == 0 && Lightning.hash(slot * 31 + i, 70 + hd) > 0.80f) {
                    float reach = (9f + 14f * Lightning.hash(slot, i + 71)) * k;
                    float side = (Lightning.hash(slot * 7 + i, 72) - 0.5f) * 12f * k;
                    float fx = px + c[2] * reach - c[3] * side;
                    float fy = py + c[3] * reach + c[2] * side;
                    Lightning.bolt(g, px, py, fx, fy, 1f, slot * 13 + i, s * f * 0.9f, BLUE, DEEP, width * 0.7f, 1, 0.6f, 0f, 3);
                }
            }
            // the head: a bloom, a white core and a glint
            float hx = hc[0];
            float hy = hc[1];
            float flick = 0.82f + 0.18f * (float) Math.sin(t * 41f + hd * 2f);
            Lightning.bloom(g, hx, hy, 9f * k + 5f, 0xFF9CC4FF, 0.85f * s * flick);
            Lightning.bloom(g, hx, hy, 3.2f * k + 1.8f, 0xFFFFFFFF, 0.95f * s * flick);
            if (!fast) {
                Lightning.streak(g, hx, hy, 14f * k, 0xFFBFD8FF, 0.8f * s * flick);
                Lightning.sparks(g, hx, hy, 5, 12f * k, t, 90 + hd, BLUE, s);
                // the stone under the head is lit by it: a soft wash that follows it, kept inside the plate
                int r = Math.max(6, Math.round(h * 0.85f));
                int wx0 = Math.max(x, Math.round(hx) - r);
                int wx1 = Math.min(x + w, Math.round(hx) + r);
                for (int cxp = wx0; cxp < wx1; cxp += 2) {
                    float d = 1f - Math.abs(cxp - hx) / r;
                    if (d <= 0f) continue;
                    g.fill(cxp, y + 1, Math.min(cxp + 2, x + w), y + h - 1, Draw.rgba(0xFF7CA8FF, 0.075f * d * d * s));
                }
            }
        }
        // small, short-lived flickers all along the edge, between the two arcs
        if (!fast) Thunder.outline(g, x, y, w, h, t, s * 0.55f, k, 0.30f);
    }

    /**
     * The flash of a click. {@code p} runs 0 to 1 over the whole effect; (cx, cy) is the middle of what was clicked and
     * (bx, by, bw, bh) its box. The whole screen is lit, so the flash goes through the menu and not just the plate.
     */
    static void click(GuiGraphics g, float cx, float cy, int bx, int by, int bw, int bh, float u, float p, int sw, int sh, int seed) {
        if (p <= 0f || p >= 1f) return;
        float k = Math.max(0.7f, u);
        // the flash: very quick up, then a long fall
        float f = p < 0.16f ? p / 0.16f : (float) Math.pow(1f - (p - 0.16f) / 0.84f, 2.2);
        // the object itself goes white-hot
        g.fill(bx, by, bx + bw, by + bh, Draw.rgba(0xFFEAF2FF, 0.80f * f));
        Lightning.bloom(g, cx, cy, Math.max(sw, sh) * (0.25f + 0.55f * p), 0xFF9CC4FF, 0.55f * f);
        Lightning.bloom(g, cx, cy, bh * 1.8f + 18f * k, 0xFFFFFFFF, 0.75f * f);
        g.fill(0, 0, sw, sh, Draw.rgba(0xFFC8DCFF, 0.42f * f));

        if (Anim.menuReady()) {
            // shock waves and bolts: rendered offline, played here
            Anim.click(g, cx, cy, Math.max(sw, sh) * 0.62f, p, 0xFFDCE8FF, 0.95f);
            return;
        }

        // two shock waves
        for (int r = 0; r < 2; r++) {
            float q = (p - r * 0.10f) / 0.62f;
            if (q <= 0f || q >= 1f) continue;
            float e = 1f - (1f - q) * (1f - q);
            float rad = e * Math.max(sw, sh) * 0.62f * (1f - 0.18f * r);
            float a = (1f - q) * (1f - q);
            int segs = 72;
            float px = cx + rad;
            float py = cy;
            for (int i = 1; i <= segs; i++) {
                float an = i * 6.2832f / segs;
                float x2 = cx + (float) Math.cos(an) * rad;
                float y2 = cy + (float) Math.sin(an) * rad * 0.62f;   // the wave is wider than tall, like the plates
                if (x2 > -40 && x2 < sw + 40) Draw.line(g, px, py, x2, y2, Math.max(1f, (3.4f - 2.2f * q) * k), Draw.rgba(0xFFDCE8FF, 0.65f * a));
                px = x2;
                py = y2;
            }
        }

        // bolts from the plate out to the sides and corners of the screen
        float grow = Math.min(1f, p / 0.20f);
        float fade = Math.max(0f, 1f - p * 1.15f);
        for (int i = 0; i < 9; i++) {
            float ang = (i / 9f) * 6.2832f + Lightning.hash(seed, i) * 0.5f;
            float len = Math.max(sw, sh) * (0.35f + 0.5f * Lightning.hash(seed, i + 20));
            float ex = cx + (float) Math.cos(ang) * len;
            float ey = cy + (float) Math.sin(ang) * len * 0.8f;
            float sx = Math.max(bx, Math.min(bx + bw, cx + (float) Math.cos(ang) * bw * 0.4f));
            float sy = cy + (float) Math.sin(ang) * bh * 0.5f;
            Lightning.bolt(g, sx, sy, ex, ey, grow, seed * 11 + i + (p > 0.3f ? 5 : 0), fade, 0xFF8EC8FF, 0xFFD6E6FF, Math.max(1.0f, 1.1f * k), 1, 0.8f, 0f, 3);
        }
    }
}
