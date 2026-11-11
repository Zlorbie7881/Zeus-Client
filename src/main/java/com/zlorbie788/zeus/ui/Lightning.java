package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class Lightning {
    private Lightning() {}

    public static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float[][] path(float x1, float y1, float x2, float y2, int seed, float amp, int levels) {
        return path(x1, y1, x2, y2, seed, amp, levels, 0.55f);
    }

    public static float[][] path(float x1, float y1, float x2, float y2, int seed, float amp, int levels, float decay) {
        int n = 1 << levels;
        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        xs[0] = x1;
        ys[0] = y1;
        xs[n] = x2;
        ys[n] = y2;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            for (int i = 0; i <= n; i++) {
                xs[i] = x1;
                ys[i] = y1;
            }
            return new float[][] {xs, ys};
        }
        float nx = -dy / len;
        float ny = dx / len;
        float a = amp;
        for (int step = n; step > 1; step /= 2) {
            int half = step / 2;
            for (int i = 0; i + step <= n; i += step) {
                int m = i + half;
                float u = m / (float) n;
                float env = (float) Math.sin(Math.PI * u);
                float off = (hash(seed, m * 7 + step) - 0.5f) * 2f * a * (0.30f + 0.70f * env);
                xs[m] = (xs[i] + xs[i + step]) / 2f + nx * off;
                ys[m] = (ys[i] + ys[i + step]) / 2f + ny * off;
            }
            a *= decay;
        }
        return new float[][] {xs, ys};
    }

    public static void seg(GuiGraphics g, float x1, float y1, float x2, float y2, float alpha, int accent, int second, float width, int layers) {
        if (layers >= 4) Draw.line(g, x1, y1, x2, y2, 8f * width, Draw.rgba(second, 0.055f * alpha));
        if (layers >= 3) Draw.line(g, x1, y1, x2, y2, 4.6f * width, Draw.rgba(accent, 0.20f * alpha));
        Draw.line(g, x1, y1, x2, y2, 2.6f * width, Draw.rgba(Draw.mix(accent, 0xFFFFFFFF, 0.55f), 0.70f * alpha));
        Draw.line(g, x1, y1, x2, y2, Math.max(1f, 1.1f * width), Draw.rgba(0xFFFFFFFF, 0.97f * alpha));
    }

    public static void seg(GuiGraphics g, float x1, float y1, float x2, float y2, float alpha, int accent, int second, float width) {
        seg(g, x1, y1, x2, y2, alpha, accent, second, width, 4);
    }

    public static void stroke(GuiGraphics g, float[][] p, float progress, float alpha, int accent, int second, float width) {
        stroke(g, p, progress, alpha, accent, second, width, 4, false);
    }

    public static void stroke(GuiGraphics g, float[][] p, float progress, float alpha, int accent, int second, float width, int layers, boolean taper) {
        int n = p[0].length - 1;
        float reach = clamp01(progress) * n;
        for (int i = 0; i < n; i++) {
            if (i >= reach) break;
            float f = Math.min(1f, reach - i);
            float ex = p[0][i] + (p[0][i + 1] - p[0][i]) * f;
            float ey = p[1][i] + (p[1][i + 1] - p[1][i]) * f;
            float w = taper ? width * (1f - 0.65f * (i / (float) n)) : width;
            seg(g, p[0][i], p[1][i], ex, ey, alpha, accent, second, w, layers);
        }
    }

    public static void bolt(GuiGraphics g, float x1, float y1, float x2, float y2, float progress, int seed, float alpha, int accent, int second, float width, int depth) {
        bolt(g, x1, y1, x2, y2, progress, seed, alpha, accent, second, width, depth, 1f);
    }

    public static void bolt(GuiGraphics g, float x1, float y1, float x2, float y2, float progress, int seed, float alpha, int accent, int second, float width, int depth, float wander) {
        bolt(g, x1, y1, x2, y2, progress, seed, alpha, accent, second, width, depth, wander, 0.3f, 4);
    }

    public static void bolt(GuiGraphics g, float x1, float y1, float x2, float y2, float progress, int seed, float alpha, int accent, int second, float width, int depth, float wander, float droop, int layers) {
        if (alpha <= 0.01f || progress <= 0f) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 3f) return;
        int levels = Math.max(3, Math.min(6, (int) Math.round(Math.log(len / 6f) / Math.log(2))));
        float[][] p = path(x1, y1, x2, y2, seed, len * 0.15f * wander, levels, 0.6f);
        stroke(g, p, progress, alpha, accent, second, width, layers, depth < 2 && wander > 0.5f);
        if (depth <= 0) return;
        int n = p[0].length - 1;
        int reach = (int) (clamp01(progress) * n);
        int branches = depth >= 2 ? 5 : 3;
        float ux = dx / len;
        float uy = dy / len;
        for (int k = 0; k < branches; k++) {
            int at = 2 + (int) (hash(seed, 100 + k) * Math.max(1, n - 4));
            if (at >= reach) continue;
            float side = hash(seed, 110 + k) < 0.5f ? -1f : 1f;
            float ang = side * (0.30f + 0.75f * hash(seed, 120 + k));
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float bdx = ux * ca - uy * sa;
            float bdy = ux * sa + uy * ca + droop;
            float bn = (float) Math.sqrt(bdx * bdx + bdy * bdy);
            bdx /= bn;
            bdy /= bn;
            float bx = p[0][at];
            float by = p[1][at];
            float bl = len * (0.14f + 0.24f * hash(seed, 130 + k)) * (1f - at / (float) n * 0.45f);
            float grow = clamp01((progress * n - at) / Math.max(2f, n * 0.25f));
            bolt(g, bx, by, bx + bdx * bl, by + bdy * bl, grow, seed * 31 + k * 17 + 7, alpha * 0.8f, accent, second, width * 0.55f, depth - 1, Math.max(0.6f, wander), droop, Math.max(2, layers - 1));
        }
    }

    public static void bloom(GuiGraphics g, float x, float y, float radius, int color, float strength) {
        if (strength <= 0.005f || radius < 1f) return;
        int levels = Math.max(3, Math.min(6, Math.round(radius / 7f)));
        for (int i = levels; i >= 1; i--) {
            float k = i / (float) levels;
            int r = Math.max(1, Math.round(radius * k));
            disc(g, Math.round(x), Math.round(y), r, Draw.rgba(color, strength * 0.075f * (1.15f - k) * (7f / levels)));
        }
    }

    public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        int stepY = r > 60 ? 3 : r > 14 ? 2 : 1;
        for (int y = -r; y <= r; y += stepY) {
            double t = y / (double) Math.max(1, r);
            int half = (int) Math.round(r * Math.sqrt(Math.max(0, 1 - t * t)));
            g.fill(cx - half, cy + y, cx + half, cy + y + stepY, color);
        }
    }

    public static void streak(GuiGraphics g, float x, float y, float halfLen, int color, float strength) {
        if (strength <= 0.01f) return;
        int cx = Math.round(x);
        int cy = Math.round(y);
        int hl = Math.round(halfLen);
        for (int i = 0; i < 4; i++) {
            float k = 1f - i / 4f;
            int l = Math.round(hl * k);
            g.fill(cx - l, cy, cx + l, cy + 1, Draw.rgba(color, strength * 0.28f));
        }
        g.fill(cx - hl / 6, cy - 1, cx + hl / 6, cy + 2, Draw.rgba(0xFFFFFFFF, strength * 0.55f));
    }

    public static void sparks(GuiGraphics g, float x, float y, int count, float radius, float t, int seed, int accent, float alpha) {
        for (int i = 0; i < count; i++) {
            float rate = 1.4f + hash(seed, i) * 1.6f;
            float ph = (t * rate + hash(seed, i + 40)) % 1f;
            float ang = hash(seed, i + 80) * 6.2832f;
            float dist = radius * (0.25f + 0.75f * ph) * (0.5f + 0.5f * hash(seed, i + 120));
            float sx = x + (float) Math.cos(ang) * dist;
            float sy = y + (float) Math.sin(ang) * dist + ph * ph * radius * 0.35f;
            float fade = (1f - ph) * alpha;
            if (fade <= 0.02f) continue;
            float tx = sx - (float) Math.cos(ang) * radius * 0.12f;
            float ty = sy - (float) Math.sin(ang) * radius * 0.12f;
            Draw.line(g, tx, ty, sx, sy, 1f, Draw.rgba(accent, 0.7f * fade));
            g.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy) + 1, Draw.rgba(0xFFFFFFFF, fade));
        }
    }
}
