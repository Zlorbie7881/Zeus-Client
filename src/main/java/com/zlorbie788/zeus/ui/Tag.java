package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class Tag {
    private Tag() {}

    private static boolean open;

    public static void begin() {
        if (open) return;
        open = true;
        Ty.begin();
    }

    public static void end() {
        if (!open) return;
        open = false;
        Ty.flush();
    }

    public static int width(String text, String right, float scale) {
        int gs = Tex.guiScale();
        float px = 8.4f * gs * scale;
        float pad = 5f * gs * scale;
        float tw = Ty.w(text, px, Ty.MED);
        float rw = right == null || right.isEmpty() ? 0f : Ty.w(right, px * 0.92f, Ty.SEMI) + 5f * gs * scale;
        return Math.round((pad * 2 + 8f * gs * scale + tw + rw) / gs);
    }

    public static void draw(GuiGraphics g, String text, String right, int cx, int top, int color, float alpha, int sw, int sh, float scale) {
        if (alpha <= 0.01f) return;
        begin();
        int gs = Tex.guiScale();
        float px = 8.4f * gs * scale;
        boolean r = right != null && !right.isEmpty();
        int hgt = Math.round(14.5f * gs * scale);
        int pad = Math.round(5f * gs * scale);
        int dotR = Math.max(2, Math.round(1.7f * gs * scale));
        int tw = Ty.w(text, px, Ty.MED);
        int rw = r ? Ty.w(right, px * 0.92f, Ty.SEMI) : 0;
        int gap = Math.round(5f * gs * scale);
        int widthDev = pad + dotR * 2 + Math.round(4f * gs * scale) + tw + (r ? gap + rw : 0) + pad;
        int x = cx * gs - widthDev / 2;
        int y = top * gs;
        x = Math.max(3 * gs, Math.min(sw * gs - widthDev - 3 * gs, x));
        y = Math.max(3 * gs, Math.min(sh * gs - hgt - 3 * gs, y));
        int rad = hgt / 2;
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1f / gs, 1f / gs);
        Ty.glow(g, x, y + gs, widthDev, hgt, rad, Math.max(4, 3 * gs), Draw.rgba(0xFF000000, 0.30f * alpha));
        Ty.fill(g, x, y, widthDev, hgt, rad, Draw.rgba(0xFF0A1020, 0.80f * alpha));
        Ty.ring(g, x, y, widthDev, hgt, rad, 1, Draw.rgba(Draw.mix(color, 0xFF0A1020, 0.35f), 0.55f * alpha));
        int cy = y + hgt / 2;
        Ty.disc(g, x + pad + dotR, cy, dotR, Draw.rgba(color, alpha));
        int tx = x + pad + dotR * 2 + Math.round(4f * gs * scale);
        Ty.text(g, text, tx, cy, px, Ty.MED, Draw.rgba(0xFFFFFFFF, alpha));
        if (r) Ty.text(g, right, tx + tw + gap, cy, px * 0.92f, Ty.SEMI, Draw.rgba(Draw.mix(color, 0xFFFFFFFF, 0.25f), alpha));
        pose.popMatrix();
    }

    public static void pill(GuiGraphics g, String text, int x, int y, int color, float alpha, int sw, int sh) {
        if (text == null || text.isEmpty() || alpha <= 0.01f) return;
        begin();
        int gs = Tex.guiScale();
        float px = 7.8f * gs;
        int tw = Ty.w(text, px, Ty.SEMI);
        int pad = Math.round(4.5f * gs);
        int hgt = Math.round(12.5f * gs);
        int w = tw + pad * 2;
        int dx = Math.max(2 * gs, Math.min(sw * gs - w - 2 * gs, x * gs));
        int dy = Math.max(2 * gs, Math.min(sh * gs - hgt - 2 * gs, y * gs));
        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1f / gs, 1f / gs);
        Ty.fill(g, dx, dy, w, hgt, hgt / 2, Draw.rgba(0xFF0A1020, 0.78f * alpha));
        Ty.ring(g, dx, dy, w, hgt, hgt / 2, 1, Draw.rgba(color, 0.50f * alpha));
        Ty.text(g, text, dx + pad, dy + hgt / 2, px, Ty.SEMI, Draw.rgba(0xFFFFFFFF, alpha));
        pose.popMatrix();
    }
}
