package com.zlorbie788.zeus.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class Draw {
    private Draw() {}

    public static boolean cheap;

    public static int a(int argb) {
        return argb >>> 24;
    }

    public static int rgba(int argb, float alpha) {
        int na = Math.max(0, Math.min(255, Math.round(a(argb) * alpha)));
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (aa << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lighten(int c, float t) {
        return mix(c, (c & 0xFF000000) | 0xFFFFFF, t);
    }

    public static int darken(int c, float t) {
        return mix(c, (c & 0xFF000000), t);
    }

    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        rrect(g, x, y, w, h, r, color, color);
    }

    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (cheap) {
            r = Math.min(r, 1);
            if (top != bottom) top = bottom = mix(top, bottom, 0.5f);
        }
        if (r == 0 && top == bottom) {
            g.fill(x, y, x + w, y + h, top);
            return;
        }
        if (top == bottom) {
            g.fill(x, y + r, x + w, y + h - r, top);
            for (int i = 0; i < r; i++) {
                int inset = r - inset(r, i);
                g.fill(x + inset, y + i, x + w - inset, y + i + 1, top);
                g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, top);
            }
            return;
        }
        int band = h > 36 ? 3 : h > 16 ? 2 : 1;
        for (int i = 0; i < h; ) {
            int rows = Math.min(band, h - i);
            if (i < r || i + rows > h - r) rows = 1;
            int inset = 0;
            if (i < r) inset = r - inset(r, i);
            else if (i >= h - r) inset = r - inset(r, h - 1 - i);
            g.fill(x + inset, y + i, x + w - inset, y + i + rows, mix(top, bottom, h <= 1 ? 0 : (i + rows / 2f) / (float) (h - 1)));
            i += rows;
        }
    }

    private static final int[][] INSETS = new int[40][];

    private static int inset(int r, int row) {
        if (r < INSETS.length) {
            int[] t = INSETS[r];
            if (t == null) {
                t = new int[r + 1];
                for (int i = 0; i <= r; i++) {
                    double dy = r - i - 0.5;
                    t[i] = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
                }
                INSETS[r] = t;
            }
            return t[Math.min(row, r)];
        }
        double dy = r - row - 0.5;
        return (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    public static void rrectLineFast(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w < 4 || h < 4) return;
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        int c = Math.max(1, r / 2);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        g.fill(x + c, y + c, x + r, y + c + 1, color);
        g.fill(x + w - r, y + c, x + w - c, y + c + 1, color);
        g.fill(x + c, y + h - c - 1, x + r, y + h - c, color);
        g.fill(x + w - r, y + h - c - 1, x + w - c, y + h - c, color);
        g.fill(x + c, y + c, x + c + 1, y + r, color);
        g.fill(x + w - c - 1, y + c, x + w - c, y + r, color);
        g.fill(x + c, y + h - r, x + c + 1, y + h - c, color);
        g.fill(x + w - c - 1, y + h - r, x + w - c, y + h - c, color);
    }

    public static void rrectLine(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w < 2 || h < 2) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (cheap) r = Math.min(r, 1);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = r - inset(r, i);
            int in2 = i == 0 ? r : r - inset(r, i - 1);
            g.fill(x + in, y + i, x + Math.max(in + 1, in2), y + i + 1, color);
            g.fill(x + w - Math.max(in + 1, in2), y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + Math.max(in + 1, in2), y + h - i, color);
            g.fill(x + w - Math.max(in + 1, in2), y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /**
     * A soft light round a box. Drawn as nine pieces of one small falloff texture (corners from its
     * quarters, edges stretched from its middle row and column) instead of one rounded rectangle per
     * pixel of spread, which cost thousands of fills a frame for a wide glow. The brightness matches
     * the old layered glow: a stack of k*k layers adds up to about spread/3 of the strength at the
     * edge and falls off as the cube of the distance.
     */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int spread, float strength) {
        if (cheap || spread <= 0 || strength <= 0f || w <= 0 || h <= 0) return;
        float peak = Math.min(1f, strength * spread / 3f);
        int tint = rgba(color, peak * ((color >>> 24) / 255f));
        Tex.Img t = glowTex();
        if (t == null) {
            for (int i = Math.min(spread, 6); i >= 1; i--) {
                float k = (1f - i / 7f);
                rrect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, rgba(color, strength * k * k));
            }
            return;
        }
        int s = spread, q = GLOW_Q;
        g.fill(x, y, x + w, y + h, tint);
        t.drawPart(g, x - s, y - s, s, s, 0, 0, q, q, tint);
        t.drawPart(g, x + w, y - s, s, s, q, 0, q, q, tint);
        t.drawPart(g, x - s, y + h, s, s, 0, q, q, q, tint);
        t.drawPart(g, x + w, y + h, s, s, q, q, q, q, tint);
        t.drawPart(g, x, y - s, w, s, q - 1, 0, 1, q, tint);
        t.drawPart(g, x, y + h, w, s, q - 1, q, 1, q, tint);
        t.drawPart(g, x - s, y, s, h, 0, q - 1, q, 1, tint);
        t.drawPart(g, x + w, y, s, h, q, q - 1, q, 1, tint);
    }

    private static final int GLOW_Q = 48;
    private static Tex.Img glowImg;
    private static boolean glowFailed;

    private static Tex.Img glowTex() {
        if (glowImg != null || glowFailed) return glowImg;
        try {
            int n = GLOW_Q * 2;
            int[] px = new int[n * n];
            for (int yy = 0; yy < n; yy++) {
                for (int xx = 0; xx < n; xx++) {
                    float dx = Math.abs(xx + 0.5f - GLOW_Q) - 0.5f, dy = Math.abs(yy + 0.5f - GLOW_Q) - 0.5f;
                    float d = (float) Math.sqrt(Math.max(0f, dx) * Math.max(0f, dx) + Math.max(0f, dy) * Math.max(0f, dy)) / GLOW_Q;
                    float a = d >= 1f ? 0f : (1f - d) * (1f - d) * (1f - d);
                    px[yy * n + xx] = (Math.round(a * 255f) << 24) | 0xFFFFFF;
                }
            }
            glowImg = Tex.made("zeus_soft_glow", px, n, n);
        } catch (RuntimeException | LinkageError e) {
            glowFailed = true;
        }
        return glowImg;
    }

    public static void sheen(GuiGraphics g, int x, int y, int w, int h, int color, float phase) {
        if (cheap || w <= 0 || h <= 0) return;
        int band = Math.max(12, w / 3);
        int travel = w + band * 2;
        float wrapped = ((phase % 1f) + 1f) % 1f;
        int start = x - band + Math.round(wrapped * travel);
        for (int i = 0; i < band; i++) {
            int px = start + i;
            if (px < x || px >= x + w) continue;
            float k = 1f - Math.abs(i / (float) band - 0.5f) * 2f;
            g.fill(px, y, px + 1, y + h, rgba(color, 0.18f * k * k));
        }
    }

    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
        if (w <= 3 || h <= 3 || len <= 0) return;
        int l = Math.min(len, Math.min(w, h) / 2);
        g.fill(x, y, x + l, y + 1, color);
        g.fill(x, y, x + 1, y + l, color);
        g.fill(x + w - l, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + l, color);
        g.fill(x, y + h - 1, x + l, y + h, color);
        g.fill(x, y + h - l, x + 1, y + h, color);
        g.fill(x + w - l, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - l, x + w, y + h, color);
    }

    public static float sweep(long periodMs) {
        return (System.currentTimeMillis() % Math.max(1L, periodMs)) / (float) Math.max(1L, periodMs);
    }

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int r, int spread, float strength) {
        if (cheap) return;
        int steps = Math.max(2, Math.min(4, spread / 2));
        int step = Math.max(1, Math.round(spread / (float) steps));
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int col = rgba(0xFF000000, strength * 0.20f * k * k);
            int o = (i + 1) * step;
            int in = Math.max(0, r - o);
            g.fill(x + in, y - o + 2, x + w - in, y - o + 2 + step, col);
            g.fill(x + in, y + h + o - step + 2, x + w - in, y + h + o + 2, col);
            g.fill(x - o, y + r, x - o + step, y + h - r + 2, col);
            g.fill(x + w + o - step, y + r, x + w + o, y + h - r + 2, col);
        }
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) {
            int s = Math.max(1, Math.round(thick));
            g.fill(Math.round(x1 - s / 2f), Math.round(y1 - s / 2f), Math.round(x1 - s / 2f) + s, Math.round(y1 - s / 2f) + s, color);
            return;
        }
        if (dy == 0 || dx == 0) {
            float h = thick / 2f;
            g.fill(Math.round(Math.min(x1, x2) - (dy == 0 ? 0 : h)), Math.round(Math.min(y1, y2) - (dx == 0 ? 0 : h)),
                    Math.round(Math.max(x1, x2) + (dy == 0 ? 0 : h)), Math.round(Math.max(y1, y2) + (dx == 0 ? 0 : h)), color);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate((float) Math.atan2(dy, dx));
        int t = Math.max(1, Math.round(thick));
        int l = (int) Math.ceil(len);
        g.fill(0, -(t / 2), l, t - (t / 2), color);
        g.pose().popMatrix();
    }

    public static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    public static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        for (int y = -ry; y <= ry; y++) {
            double t = y / (double) Math.max(1, ry);
            int half = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - t * t)));
            g.fill(cx - half, cy + y, cx + half, cy + y + 1, color);
        }
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    private static final java.util.HashMap<String, Integer> WIDTHS = new java.util.HashMap<>();
    private static final java.util.HashMap<String, Integer> WIDTHS_BOLD = new java.util.HashMap<>();
    private static final java.util.HashMap<String, String> FITS = new java.util.HashMap<>();

    public static int width(String s) {
        Integer w = WIDTHS.get(s);
        if (w == null) {
            if (WIDTHS.size() > 3000) WIDTHS.clear();
            w = font().width(Component.literal(s));
            WIDTHS.put(s, w);
        }
        return w;
    }

    public static int widthBold(String s) {
        Integer w = WIDTHS_BOLD.get(s);
        if (w == null) {
            if (WIDTHS_BOLD.size() > 1000) WIDTHS_BOLD.clear();
            w = font().width(Component.literal(s).withStyle(ChatFormatting.BOLD));
            WIDTHS_BOLD.put(s, w);
        }
        return w;
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), Component.literal(s), x, y, color, false);
    }

    public static void textShadow(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), Component.literal(s), x, y, color, true);
    }

    public static void bold(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, color, false);
    }

    public static void textRight(GuiGraphics g, String s, int xRight, int y, int color) {
        text(g, s, xRight - width(s), y, color);
    }

    public static void textCenter(GuiGraphics g, String s, int cx, int y, int color) {
        text(g, s, cx - width(s) / 2, y, color);
    }

    public static void boldCenter(GuiGraphics g, String s, int cx, int y, int color) {
        bold(g, s, cx - widthBold(s) / 2, y, color);
    }

    public static String fit(String s, int max) {
        if (width(s) <= max) return s;
        String key = max + "|" + s;
        String cached = FITS.get(key);
        if (cached != null) return cached;
        if (FITS.size() > 2000) FITS.clear();
        String out = fitSlow(s, max);
        FITS.put(key, out);
        return out;
    }

    private static String fitSlow(String s, int max) {
        String dots = "...";
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (width(s.substring(0, mid) + dots) <= max) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + dots;
    }

    public static int widthS(String s, float sc) {
        return Math.round(width(s) * sc);
    }

    public static int widthBoldS(String s, float sc) {
        return Math.round(widthBold(s) * sc);
    }

    public static void textS(GuiGraphics g, String s, int x, int y, int color, float sc) {
        if (sc == 1f) {
            text(g, s, x, y, color);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        text(g, s, 0, 0, color);
        g.pose().popMatrix();
    }

    public static void boldS(GuiGraphics g, String s, int x, int y, int color, float sc) {
        if (sc == 1f) {
            bold(g, s, x, y, color);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        bold(g, s, 0, 0, color);
        g.pose().popMatrix();
    }

    public static void textCenterS(GuiGraphics g, String s, int cx, int y, int color, float sc) {
        textS(g, s, cx - widthS(s, sc) / 2, y, color, sc);
    }

    public static void boldCenterS(GuiGraphics g, String s, int cx, int y, int color, float sc) {
        boldS(g, s, cx - Math.round(widthBoldS(s, sc) / 2f), y, color, sc);
    }

    public static void textRightS(GuiGraphics g, String s, int xRight, int y, int color, float sc) {
        textS(g, s, xRight - widthS(s, sc), y, color, sc);
    }

    public static int spaced(GuiGraphics g, String s, int x, int y, int color, float sc, int gap, boolean bold) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            if (bold) boldS(g, c, cx, y, color, sc);
            else textS(g, c, cx, y, color, sc);
            cx += (bold ? widthBoldS(c, sc) : widthS(c, sc)) + gap;
        }
        return cx - x - gap;
    }

    public static int spacedWidth(String s, float sc, int gap, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            w += (bold ? widthBoldS(c, sc) : widthS(c, sc)) + gap;
        }
        return Math.max(0, w - gap);
    }

    public static void toast(GuiGraphics g, Minecraft mc, int cx, int y, String text, boolean good, float alpha) {
        toast(g, mc, cx, y, text, good ? Accent.OK : 0xFFFF6B6B, alpha);
    }

    public static void toast(GuiGraphics g, Minecraft mc, int cx, int y, String text, int accent, float alpha) {
        toast(g, mc, cx, y, text, accent, alpha, 1f);
    }

    public static void toast(GuiGraphics g, Minecraft mc, int cx, int y, String text, int accent, float alpha, float progress) {
        if (alpha <= 0.01f) return;
        int maxWidth = Math.max(90, mc.getWindow().getGuiScaledWidth() - 24);
        String shown = fit(text, maxWidth - 34);
        int w = Math.min(maxWidth, width(shown) + 34);
        int x = cx - w / 2;
        shadow(g, x, y, w, 18, 5, 5, alpha * 0.9f);
        rrect(g, x, y, w, 18, 5, rgba(Accent.surface(0xFF0C1019), 0.95f * alpha), rgba(Accent.surface(0xFF080B12), 0.95f * alpha));
        g.fill(x + 2, y + 1, x + w - 2, y + 2, rgba(0xFFFFFFFF, 0.07f * alpha));
        rrect(g, x + 1, y + 3, 3, 12, 2, rgba(accent, alpha));
        if (!cheap) glow(g, x + 1, y + 3, 3, 12, 2, accent, 4, 0.45f * alpha);
        rrectLine(g, x, y, w, 18, 5, rgba(accent, 0.42f * alpha));
        text(g, shown, x + 16, y + 5, rgba(0xFFE9EEFB, alpha));
        int bar = Math.round((w - 6) * Math.max(0f, Math.min(1f, progress)));
        if (bar > 0) g.fill(x + 3, y + 16, x + 3 + bar, y + 17, rgba(accent, 0.50f * alpha));
    }
}
