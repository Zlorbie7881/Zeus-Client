package com.zlorbie788.zeus.render;

import com.zlorbie788.zeus.ui.Draw;
import com.zlorbie788.zeus.ui.Tex;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the lines of the overlays (ESP boxes, tracers, brackets) as smooth sprites that were rendered offline
 * (tools/anim/make_strokes.py), instead of flat rectangles.
 *
 * <p>Every line is <b>one</b> picture that is rotated and stretched along the line: the soft line carries its glow and its
 * crisp core in one piece, the tracer carries its taper and fade, the glint its tail. That is smoother (the edges are
 * anti-aliased, a flat rotated rectangle is not) and a lot cheaper, because the game keeps a record of every shape drawn in a
 * frame and a line that was drawn as three rectangles, or sixteen segments, costs three or sixteen times as much to keep.</p>
 *
 * <p>Lines are cut to the screen first. A box that is much bigger than the screen (you are zoomed in on something close)
 * would otherwise be a handful of huge shapes that overlap everything else; cut, they are small. A budget per frame drops the
 * optional layers (keylines) when a lot is on the screen at once.</p>
 */
public final class Stroke {
    private Stroke() {}

    private static final String SHEET = "textures/gui/anim/stroke.png";
    private static final String MARKS = "textures/gui/anim/marks.png";
    private static boolean failed;

    // regions of stroke.png
    private static final int SOFT_Y = 0, SOFT_H = 32;
    private static final int KEY_Y = 36, KEY_H = 16;
    private static final int TAPER_Y = 56, TAPER_H = 48;
    private static final int GLINT_Y = 108, GLINT_H = 32;
    private static final int HARD_Y = 144, HARD_H = 16;
    private static final int SW = 256;

    private static long frameId = Long.MIN_VALUE;
    private static int spent;

    /** Called once a frame by the projector. Starts the budget again. */
    static void frame(long id) {
        if (id != frameId) {
            frameId = id;
            spent = 0;
        }
    }

    private static Tex.Img sheet() {
        if (failed) return null;
        Tex.Img img = Tex.sprite(SHEET);
        if (img == null) failed = true;
        return img;
    }

    private static Tex.Img marks() {
        if (failed) return null;
        Tex.Img img = Tex.sprite(MARKS);
        if (img == null) failed = true;
        return img;
    }

    /** True while there is still room in this frame's budget for the nice-to-have layers. */
    public static boolean room() {
        return spent < 700;
    }

    private static final float[] CLIP = new float[6];

    /**
     * Cuts the segment to the screen (plus a margin). Returns false when nothing of it is on the screen. On true,
     * out = x1, y1, x2, y2, t0, t1 where t0 and t1 are where on the original line the kept part starts and ends (0 to 1).
     */
    public static boolean clip(float x1, float y1, float x2, float y2, float[] out) {
        float w = Projector.width();
        float h = Projector.height();
        if (w <= 0 || h <= 0) {
            out[0] = x1; out[1] = y1; out[2] = x2; out[3] = y2; out[4] = 0f; out[5] = 1f;
            return true;
        }
        float m = 24f;
        float xmin = -m, ymin = -m, xmax = w + m, ymax = h + m;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float t0 = 0f;
        float t1 = 1f;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {x1 - xmin, xmax - x1, y1 - ymin, ymax - y1};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1e-6f) {
                if (q[i] < 0f) return false;
            } else {
                float r = q[i] / p[i];
                if (p[i] < 0f) {
                    if (r > t1) return false;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return false;
                    if (r < t1) t1 = r;
                }
            }
        }
        out[0] = x1 + dx * t0;
        out[1] = y1 + dy * t0;
        out[2] = x1 + dx * t1;
        out[3] = y1 + dy * t1;
        out[4] = t0;
        out[5] = t1;
        return true;
    }

    /**
     * One rotated, stretched piece of the sheet. The picture region is (sx, sy, sw, sh); it is drawn {@code len + 2 * ext}
     * long and {@code thickness} tall, centred on the line.
     */
    private static void quad(GuiGraphics g, Tex.Img img, float x1, float y1, float x2, float y2, float ext, float thickness,
                             int sx, int sy, int sw, int sh, int tint) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f || thickness <= 0.01f) return;
        spent++;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        pose.translate(-ext, -thickness / 2f);
        pose.scale((len + 2f * ext) / 64f, thickness / sh);
        img.drawPart(g, 0, 0, 64, sh, sx, sy, sw, sh, tint);
        pose.popMatrix();
    }

    /** A line with its glow: crisp core of {@code thick} pixels, halo around it. {@code color} carries the alpha. */
    public static void soft(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int color) {
        if (!clip(x1, y1, x2, y2, CLIP)) return;
        Tex.Img s = sheet();
        if (s == null) {
            Draw.line(g, CLIP[0], CLIP[1], CLIP[2], CLIP[3], thick, color);
            return;
        }
        if (spent > 1600) return;
        quad(g, s, CLIP[0], CLIP[1], CLIP[2], CLIP[3], thick * 0.5f, thick * 8f, 0, SOFT_Y, 16, SOFT_H, color);
    }

    /** The dark keyline under a line, so it reads on a bright background. Skipped when the frame is busy. */
    public static void keyline(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, float alpha) {
        if (!room() || alpha <= 0.01f) return;
        if (!clip(x1, y1, x2, y2, CLIP)) return;
        Tex.Img s = sheet();
        if (s == null) return;
        float t = thick + 1.2f;
        quad(g, s, CLIP[0], CLIP[1], CLIP[2], CLIP[3], t * 0.5f, 2f * t, 0, KEY_Y, 16, KEY_H, Draw.rgba(0xFF04060C, alpha));
    }

    /** A tracer stroke: thin and faint at (x1, y1), full colour and thicker at (x2, y2). */
    public static void taper(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int color) {
        if (!clip(x1, y1, x2, y2, CLIP)) return;
        Tex.Img s = sheet();
        if (s == null) {
            Draw.line(g, CLIP[0], CLIP[1], CLIP[2], CLIP[3], thick, color);
            return;
        }
        int sx = Math.round(CLIP[4] * (SW - 1));
        int sw = Math.max(2, Math.round((CLIP[5] - CLIP[4]) * (SW - 1)));
        if (sx + sw > SW) sx = SW - sw;
        quad(g, s, CLIP[0], CLIP[1], CLIP[2], CLIP[3], 0f, thick * 12f, sx, TAPER_Y, sw, TAPER_H, color);
    }

    /** The bright travelling piece: the part of the line from f0 to f1 (0 to 1), brightest at f1. */
    public static void glint(GuiGraphics g, float x1, float y1, float x2, float y2, float f0, float f1, float thick, int color) {
        Tex.Img s = sheet();
        if (s == null || f1 <= f0) return;
        float ax = x1 + (x2 - x1) * f0;
        float ay = y1 + (y2 - y1) * f0;
        float bx = x1 + (x2 - x1) * f1;
        float by = y1 + (y2 - y1) * f1;
        if (!clip(ax, ay, bx, by, CLIP)) return;
        int sx = Math.round(CLIP[4] * (SW - 1));
        int sw = Math.max(2, Math.round((CLIP[5] - CLIP[4]) * (SW - 1)));
        if (sx + sw > SW) sx = SW - sw;
        quad(g, s, CLIP[0], CLIP[1], CLIP[2], CLIP[3], 0f, thick * 8f, sx, GLINT_Y, sw, GLINT_H, color);
    }

    /** A glowing dot of about {@code r} pixels radius. */
    public static void dot(GuiGraphics g, float x, float y, float r, int color) {
        Tex.Img m = marks();
        if (m == null || r <= 0.2f) return;
        spent++;
        float d = r * 5.3f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x - d / 2f, y - d / 2f);
        pose.scale(d / 64f, d / 64f);
        m.drawPart(g, 0, 0, 64, 64, 0, 0, 64, 64, color);
        pose.popMatrix();
    }

    /** A diamond outline of radius {@code r} pixels with a dark keyline. */
    public static void diamond(GuiGraphics g, float x, float y, float r, int color, float keyAlpha) {
        Tex.Img m = marks();
        if (m == null) return;
        float d = r * 64f / 22f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x - d / 2f, y - d / 2f);
        pose.scale(d / 64f, d / 64f);
        if (room() && keyAlpha > 0.01f) m.drawPart(g, 0, 0, 64, 64, 128, 0, 64, 64, Draw.rgba(0xFF04060C, keyAlpha));
        m.drawPart(g, 0, 0, 64, 64, 64, 0, 64, 64, color);
        pose.popMatrix();
        spent += 2;
    }

    /** A flat, crisp line of {@code thick} pixels with no glow: the plain look of a world line. */
    public static void hard(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int color) {
        if (!clip(x1, y1, x2, y2, CLIP)) return;
        Tex.Img s = sheet();
        if (s == null) {
            Draw.line(g, CLIP[0], CLIP[1], CLIP[2], CLIP[3], Math.max(1f, thick), color);
            return;
        }
        if (spent > 2400) return;
        quad(g, s, CLIP[0], CLIP[1], CLIP[2], CLIP[3], 0f, thick * 2f, 0, HARD_Y, 16, HARD_H, color);
    }

    /**
     * A filled triangle arrow of size {@code size} pixels (the tip is 1.1547 * size from the middle) whose middle is at
     * (cx, cy) and that points at {@code angle} radians.
     */
    public static void arrow(GuiGraphics g, float cx, float cy, float size, float angle, int color) {
        Tex.Img m = marks();
        if (m == null || size <= 0.2f) return;
        spent++;
        float d = size * 64f / 22f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate(angle);
        pose.translate(-d / 2f, -d / 2f);
        pose.scale(d / 64f, d / 64f);
        m.drawPart(g, 0, 0, 64, 64, 192, 0, 64, 64, color);
        pose.popMatrix();
    }

    private static boolean triFailed;

    /** A solid triangle with corners (ax, ay), (bx, by), (cx, cy), in GUI pixels. {@code color} carries the alpha. */
    public static void tri(GuiGraphics g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        if (triFailed || spent > 2400) return;
        Tex.Img t = Tex.sprite("textures/gui/anim/tri.png");
        if (t == null) {
            triFailed = true;
            return;
        }
        float ux = (bx - ax) / 126f;
        float uy = (by - ay) / 126f;
        float vx = (cx - ax) / 126f;
        float vy = (cy - ay) / 126f;
        spent++;
        var pose = g.pose();
        pose.pushMatrix();
        pose.mul(new org.joml.Matrix3x2f(ux, uy, vx, vy, ax - ux - vx, ay - uy - vy));
        t.drawPart(g, 0, 0, 128, 128, 0, 0, 128, 128, color);
        pose.popMatrix();
    }
}
