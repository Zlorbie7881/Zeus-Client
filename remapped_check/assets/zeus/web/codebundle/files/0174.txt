package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Plays the animations that were rendered offline (tools/anim/make_arcs.py) as sprite sheets. Nothing here draws a bolt: it
 * only picks the frame for the current time and shows that picture, tinted with the accent colour. Everything is in screen
 * pixels (the panel and the windows draw with the pose scaled down by the GUI scale).
 */
final class Anim {
    private Anim() {}

    private static final int ARC_W = 512;
    private static final int ARC_H = 64;
    private static final int BURST = 192;
    private static final int FRAMES = 16;
    private static final int COLS = 4;
    private static final float FPS = 22f;

    private static boolean failed;

    private static Tex.Img sheet(String name) {
        if (failed) return null;
        try {
            Tex.Img img = Tex.sprite("textures/gui/anim/" + name + ".png");
            if (img == null) failed = true;
            return img;
        } catch (Throwable t) {
            failed = true;
            return null;
        }
    }

    private static void frame(GuiGraphics g, Tex.Img img, int fw, int fh, int f, int x, int y, int w, int h, int tint, boolean flipV) {
        if (img == null || w <= 0 || h <= 0) return;
        int sx = (f % COLS) * fw;
        int sy = (f / COLS) * fh;
        img.drawPart(g, x, y, w, h, sx, sy, fw, fh, tint);
    }

    /** One flowing arc, {@code w} wide, centred on the line {@code cy}, {@code h} high. Phase shifts it in time so two arcs differ. */
    static void arc(GuiGraphics g, int x, int cy, int w, int h, float t, float phase, int glowTint, int coreTint, float alpha) {
        Tex.Img glow = sheet("arc_glow");
        Tex.Img core = sheet("arc_core");
        if (glow == null || core == null || alpha <= 0.01f) return;
        int f = Math.floorMod((int) Math.floor((t + phase) * FPS), FRAMES);
        frame(g, glow, ARC_W, ARC_H, f, x, cy - h / 2, w, h, Draw.rgba(glowTint, 0.85f * alpha), false);
        frame(g, core, ARC_W, ARC_H, f, x, cy - h / 2, w, h, Draw.rgba(coreTint, alpha), false);
    }

    /** An arc that circles a rounded-square button of {@code size} pixels whose top left corner is (x, y). */
    static void ring(GuiGraphics g, int x, int y, int size, float t, int glowTint, int coreTint, float alpha) {
        Tex.Img glow = sheet("ring_glow");
        Tex.Img core = sheet("ring_core");
        if (glow == null || core == null || alpha <= 0.01f) return;
        int f = Math.floorMod((int) Math.floor(t * 18f), FRAMES);
        int d = Math.round(size * 160f / 108.8f);
        int ox = x + size / 2 - d / 2;
        int oy = y + size / 2 - d / 2;
        frame(g, glow, 160, 160, f, ox, oy, d, d, Draw.rgba(glowTint, 0.9f * alpha), false);
        frame(g, core, 160, 160, f, ox, oy, d, d, Draw.rgba(coreTint, alpha), false);
    }

    /** One frame of the burst picked from {@code p}, 0 to 1 over its life, centred on (cx, cy), {@code size} wide. */
    static void burst(GuiGraphics g, float cx, float cy, int size, float p, int glowTint, int coreTint, float alpha) {
        Tex.Img glow = sheet("burst_glow");
        Tex.Img core = sheet("burst_core");
        if (glow == null || core == null || p < 0f || p >= 1f) return;
        int f = Math.min(FRAMES - 1, (int) (p * FRAMES));
        int x = Math.round(cx - size / 2f);
        int y = Math.round(cy - size / 2f);
        frame(g, glow, BURST, BURST, f, x, y, size, size, Draw.rgba(glowTint, 0.9f * alpha), false);
        frame(g, core, BURST, BURST, f, x, y, size, size, Draw.rgba(coreTint, alpha), false);
    }

    // ------------------------------------------------------------------ menu plates, round buttons, click flash

    private static final int PLATE_W = 512;
    private static final int PLATE_H = 96;
    private static final int PLATE_M = 16;        // margin round the plate in the picture
    private static final int PLATE_BODY_H = 64;   // the plate itself, in the picture
    private static final int PLATE_CAP = 96;      // width of each end piece in the picture
    private static final int PLATE_FRAMES = 24;

    /** True when the menu sheets could be read, so the caller can fall back to something else when not. */
    static boolean menuReady() {
        return sheet("plate_glow") != null && sheet("plate_core") != null && sheet("circ_glow") != null && sheet("click") != null;
    }

    /**
     * Two arcs that chase each other round the edge of a plate whose top left corner is (x, y), {@code w} by {@code h}. The
     * picture is cut in three (the two ends and the middle) so the corners keep their shape at any width.
     */
    static void plate(GuiGraphics g, int x, int y, int w, int h, float t, int glowTint, int coreTint, float alpha) {
        Tex.Img glow = sheet("plate_glow");
        Tex.Img core = sheet("plate_core");
        if (glow == null || core == null || alpha <= 0.01f || w < 8 || h < 6) return;
        int f = Math.floorMod((int) Math.floor(t * 20f), PLATE_FRAMES);
        float s = h / (float) PLATE_BODY_H;
        int m = Math.round(PLATE_M * s);
        int dh = Math.round(PLATE_H * s);
        int dy = y - m;
        int totalW = w + 2 * m;
        int cap = Math.round(PLATE_CAP * s);
        int fx = (f % COLS) * PLATE_W;
        int fy = (f / COLS) * PLATE_H;
        int gt = Draw.rgba(glowTint, 0.95f * alpha);
        int ct = Draw.rgba(coreTint, alpha);
        for (int layer = 0; layer < 2; layer++) {
            Tex.Img img = layer == 0 ? glow : core;
            int tint = layer == 0 ? gt : ct;
            if (totalW <= 2 * cap + 4) {
                img.drawPart(g, x - m, dy, totalW, dh, fx, fy, PLATE_W, PLATE_H, tint);
                continue;
            }
            int midSrc = PLATE_W - 2 * PLATE_CAP;
            img.drawPart(g, x - m, dy, cap, dh, fx, fy, PLATE_CAP, PLATE_H, tint);
            img.drawPart(g, x - m + cap, dy, totalW - 2 * cap, dh, fx + PLATE_CAP, fy, midSrc, PLATE_H, tint);
            img.drawPart(g, x - m + totalW - cap, dy, cap, dh, fx + PLATE_W - PLATE_CAP, fy, PLATE_CAP, PLATE_H, tint);
        }
    }

    /** Two arcs that circle a round button of radius {@code r} (pixels) centred on (cx, cy). */
    static void circ(GuiGraphics g, float cx, float cy, float r, float t, int glowTint, int coreTint, float alpha) {
        Tex.Img glow = sheet("circ_glow");
        Tex.Img core = sheet("circ_core");
        if (glow == null || core == null || alpha <= 0.01f || r < 3f) return;
        int f = Math.floorMod((int) Math.floor(t * 20f), PLATE_FRAMES);
        int d = Math.round(160f * r / 56f);
        int ox = Math.round(cx - d / 2f);
        int oy = Math.round(cy - d / 2f);
        frame(g, glow, 160, 160, f, ox, oy, d, d, Draw.rgba(glowTint, 0.95f * alpha), false);
        frame(g, core, 160, 160, f, ox, oy, d, d, Draw.rgba(coreTint, alpha), false);
    }

    /**
     * The click: shock waves and bolts from (cx, cy), {@code p} 0 to 1 over its life. {@code reach} is the half width the waves
     * grow to, in pixels.
     */
    static void click(GuiGraphics g, float cx, float cy, float reach, float p, int tint, float alpha) {
        Tex.Img img = sheet("click");
        if (img == null || p < 0f || p >= 1f) return;
        int f = Math.min(15, (int) (p * 16f));
        float s = reach / 240f;
        int dw = Math.round(512 * s);
        int dh = Math.round(256 * s);
        frame(g, img, 512, 256, f, Math.round(cx - dw / 2f), Math.round(cy - dh / 2f), dw, dh, Draw.rgba(tint, alpha), false);
    }
}
