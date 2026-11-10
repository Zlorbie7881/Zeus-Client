package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/** The Zeus mark: a Z drawn as a bolt of lightning. It has the same outline as the Z of the intro. */
public final class Logo {
    private Logo() {}

    /** The Z in a box of {@code size} pixels, centred on (cx, cy). */
    public static void z(GuiGraphics g, float cx, float cy, float size, int color, int glow, float glowStrength) {
        float[][] u = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}};
        float[][] p = new float[4][2];
        for (int i = 0; i < 4; i++) {
            p[i][0] = cx + (u[i][0] - u[i][1] * 0.10f) * size;
            p[i][1] = cy + u[i][1] * size;
        }
        float t = Math.max(1.6f, size / 8f);
        if (glowStrength > 0f) {
            for (int s = 0; s < 3; s++) {
                Draw.line(g, p[s][0], p[s][1], p[s + 1][0], p[s + 1][1], t * 3.2f, Draw.rgba(glow, 0.10f * glowStrength));
                Draw.line(g, p[s][0], p[s][1], p[s + 1][0], p[s + 1][1], t * 2f, Draw.rgba(glow, 0.22f * glowStrength));
            }
        }
        for (int s = 0; s < 3; s++) {
            Draw.line(g, p[s][0], p[s][1], p[s + 1][0], p[s + 1][1], t, color);
        }
        // round the corners
        for (int i = 0; i < 4; i++) {
            int r = Math.round(t / 2f);
            g.fill(Math.round(p[i][0]) - r, Math.round(p[i][1]) - r, Math.round(p[i][0]) + r, Math.round(p[i][1]) + r, color);
        }
    }

    /** A small Z with its upper-left corner at (x, y). */
    public static void bolt(GuiGraphics g, int x, int y, int size, int color) {
        z(g, x + size / 2f, y + size / 2f, size * 0.78f, color, color, 0f);
    }

    /**
     * The living mark: the same Z, drawn as a bundle of electric strands. Each stroke is three jagged bolts packed
     * together on top of a bright straight core, redrawn about 18 times a second so it crackles, with sparks, small arcs
     * jumping off the corners and a glow that breathes. {@code time} is in seconds.
     */
    public static void packed(GuiGraphics g, float cx, float cy, float size, float time, int accent, int second, float strength) {
        float[][] u = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}};
        float[][] p = new float[4][2];
        for (int i = 0; i < 4; i++) {
            p[i][0] = cx + (u[i][0] - u[i][1] * 0.10f) * size;
            p[i][1] = cy + u[i][1] * size;
        }
        int frame = (int) (time * 18f);
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 5.0);
        float unit = Math.max(0.55f, size / 34f);
        // small marks (the panel corner) use fewer strands and layers, so the animation costs very little
        boolean mini = size < 44f;
        int strands = mini ? 2 : 3;
        int levels = mini ? 2 : 3;
        int layers = mini ? 2 : 3;
        Lightning.bloom(g, cx, cy, size * (mini ? 0.8f : 0.95f), accent, strength * pulse);
        // the strands
        for (int s = 0; s < 3; s++) {
            for (int k = 0; k < strands; k++) {
                int seed = frame * 13 + s * 101 + k * 977;
                float amp = size * (k == 0 ? 0.075f : 0.05f);
                float[][] path = Lightning.path(p[s][0], p[s][1], p[s + 1][0], p[s + 1][1], seed, amp, levels);
                float a = (k == 0 ? 0.95f : 0.65f) * strength;
                Lightning.stroke(g, path, 1f, a, accent, second, Math.max(0.45f, unit * (k == 0 ? 0.9f : 0.6f)), layers, false);
            }
        }
        // the straight core keeps the shape readable at any size
        float t = Math.max(1.3f, size / 15f);
        for (int s = 0; s < 3; s++) {
            Draw.line(g, p[s][0], p[s][1], p[s + 1][0], p[s + 1][1], t, Draw.rgba(Draw.mix(accent, 0xFFFFFFFF, 0.78f), 0.85f * strength));
        }
        for (int i = 0; i < 4; i++) {
            int r = Math.max(1, Math.round(t / 2f));
            g.fill(Math.round(p[i][0]) - r, Math.round(p[i][1]) - r, Math.round(p[i][0]) + r, Math.round(p[i][1]) + r, Draw.rgba(0xFFFFFFFF, 0.95f * strength));
        }
        // a bright pulse running along the Z
        float run = (time * 0.9f) % 1f * 3f;
        int seg = Math.min(2, (int) run);
        float f = run - seg;
        float hx = p[seg][0] + (p[seg + 1][0] - p[seg][0]) * f;
        float hy = p[seg][1] + (p[seg + 1][1] - p[seg][1]) * f;
        Lightning.bloom(g, hx, hy, size * 0.32f, 0xFFFFFFFF, 0.9f * strength);
        // arcs off the corners, now and then
        for (int c = 0; c < (mini ? 2 : 4); c++) {
            if (Lightning.hash(frame, c + 3) > 0.30f) continue;
            float ang = Lightning.hash(frame, c + 9) * 6.2832f;
            float len = size * (0.22f + 0.2f * Lightning.hash(frame, c + 21));
            Lightning.bolt(g, p[c][0], p[c][1], p[c][0] + (float) Math.cos(ang) * len, p[c][1] + (float) Math.sin(ang) * len, 1f, frame * 7 + c, 0.8f * strength, accent, second, Math.max(0.4f, unit * 0.5f), 0);
        }
        Lightning.sparks(g, cx, cy, mini ? 4 : 8, size * 0.9f, time, 5, accent, strength);
    }

    /**
     * The living mark made from your own Z picture: the picture itself, a breathing blue glow under it, a bright shine that
     * sweeps across it now and then, thin arcs of electricity that jump off its edge, and a few sparks. It costs three
     * pictures and a handful of lines a frame. Returns false when the pictures could not be loaded, so the caller can draw
     * the vector Z instead. {@code slot} keeps pictures of different sizes apart.
     */
    public static boolean live(String slot, GuiGraphics g, float cx, float cy, float size, float time, int accent, int second, float strength) {
        if (strength <= 0.01f) return true;
        int gs = Tex.guiScale();
        int px = Math.max(24, Math.round(size * gs));
        px = (px + 7) / 8 * 8;
        Tex.Img base = Tex.fitted("logo_" + slot, "textures/gui/logo_z.png", px, px, false, 0.5f, 0.5f);
        Tex.Img hot = Tex.fitted("logo_hot_" + slot, "textures/gui/logo_z_hot.png", px, px, false, 0.5f, 0.5f);
        Tex.Img glow = Tex.fitted("logo_glow_" + slot, "textures/gui/logo_z_glow.png", px, px, false, 0.5f, 0.5f);
        if (base == null || hot == null || glow == null) return false;
        float breathe = 0.5f + 0.5f * (float) Math.sin(time * 2.6);
        float pulse = 1f + 0.018f * (float) Math.sin(time * 2.6);
        int d = Math.round(px / (float) gs * pulse);
        int x = Math.round(cx - d / 2f);
        int y = Math.round(cy - d / 2f);
        // the glow breathes and is larger than the picture
        int gd = Math.round(d * 1.35f);
        glow.draw(g, Math.round(cx - gd / 2f), Math.round(cy - gd / 2f), gd, gd, Draw.rgba(0xFFFFFFFF, (0.45f + 0.4f * breathe) * strength));
        base.draw(g, x, y, d, d, Draw.rgba(0xFFFFFFFF, strength));
        // the shine: a soft vertical band sliding left to right, once every few seconds
        float cycle = (time * 0.42f) % 1.9f;
        float centre = cycle - 0.3f;
        if (centre > -0.2f && centre < 1.2f) {
            for (int b = -3; b <= 3; b++) {
                float f = centre + b * 0.035f;
                float fall = 1f - Math.abs(b) / 4f;
                int sx = Math.round(f * px);
                int sw = Math.max(1, Math.round(px * 0.035f));
                if (sx < 0 || sx + sw > px) continue;
                int dx = Math.round(sx * (d / (float) px));
                int dw = Math.max(1, Math.round(sw * (d / (float) px)));
                hot.drawPart(g, x + dx, y, dw, d, sx, 0, sw, px, Draw.rgba(0xFFFFFFFF, 0.85f * fall * strength));
            }
        }
        // electricity jumping off the edge of the Z (the points sit on the strokes of the Z in the picture)
        float[][] pts = {{0.18f, 0.2f}, {0.82f, 0.2f}, {0.52f, 0.5f}, {0.2f, 0.82f}, {0.8f, 0.82f}, {0.5f, 0.18f}, {0.5f, 0.84f}};
        int frame = (int) (time * 12f);
        int arcs = size < 44f ? 1 : 3;
        for (int c = 0; c < arcs; c++) {
            if (Lightning.hash(frame, c + 41) > 0.55f) continue;
            float[] p = pts[(int) (Lightning.hash(frame, c + 5) * pts.length) % pts.length];
            float ax = x + p[0] * d;
            float ay = y + p[1] * d;
            float ang = Lightning.hash(frame, c + 17) * 6.2832f;
            float len = d * (0.18f + 0.22f * Lightning.hash(frame, c + 29));
            Lightning.bolt(g, ax, ay, ax + (float) Math.cos(ang) * len, ay + (float) Math.sin(ang) * len, 1f, frame * 13 + c, 0.85f * strength, accent, second, Math.max(0.5f, d / 70f), 0);
        }
        Lightning.sparks(g, cx, cy, size < 44f ? 3 : 7, d * 0.7f, time, 9, accent, strength);
        return true;
    }
}
