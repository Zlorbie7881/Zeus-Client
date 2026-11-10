package com.zlorbie788.zeus.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The picture behind the panel: a deep blue night, soft glows, a header band and a little branching lightning in the
 * top-right corner, drawn once with Java2D and shown with a single image call per frame. It never touches the middle
 * where the module list sits, so text stays easy to read.
 */
final class PanelArt {
    private PanelArt() {}

    private static final class Bolt {
        final double x1, y1, x2, y2, wander;
        final int seed;
        final float width;

        Bolt(double x1, double y1, double x2, double y2, double wander, int seed, float width) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.wander = wander;
            this.seed = seed;
            this.width = width;
        }
    }

    // positions are fractions of the picture (x right, y down); the storm lives in the header corner and along the left edge
    private static final Bolt[] BOLTS = {
        // down the left and right edges, faint
        new Bolt(0.006, 0.350, 0.013, 0.680, 0.05, 19, 0.9f),
        new Bolt(0.994, 0.560, 0.990, 0.740, 0.05, 24, 0.8f),
    };

    /** The night behind the panel: a deep blue gradient, soft glows, a header band and a little lightning. */
    static int[] render(int w, int h, int radius) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setClip(new RoundRectangle2D.Double(0, 0, w, h, radius * 2.0, radius * 2.0));
        g.setPaint(new GradientPaint(0, 0, new Color(9, 16, 36, 244), 0, h, new Color(5, 9, 20, 247)));
        g.fillRect(0, 0, w, h);
        glow(g, w * 0.82, -h * 0.06, w * 0.34, 0.30f, 47, 134, 255);
        glow(g, w * 0.00, h * 1.00, w * 0.30, 0.12f, 47, 134, 255);
        int head = Math.round(h * 88f / 925f);
        g.setPaint(new GradientPaint(0, 0, new Color(47, 134, 255, 18), 0, head, new Color(47, 134, 255, 0)));
        g.fillRect(0, 0, w, head);
        g.setColor(new Color(96, 150, 255, 40));
        g.fillRect(0, head, w, Math.max(1, h / 900));
        // lightning: the big storm only over the header, the thin ones along the edges
        double s = Math.max(0.5, w / 1672.0);
        Shape keep = g.getClip();
        for (int i = 0; i < BOLTS.length; i++) {
            Bolt b = BOLTS[i];
            if (i < 4) g.setClip(new java.awt.Rectangle(0, 0, w, head));
            else g.setClip(keep);
            bolt(g, b.x1 * w, b.y1 * h, b.x2 * w, b.y2 * h, b.wander, b.seed, (float) (b.width * s), 1);
        }
        g.setClip(keep);
        g.dispose();
        return img.getRGB(0, 0, w, h, null, 0, w);
    }

    private static void glow(Graphics2D g, double cx, double cy, double r, float strength, int rr, int gg, int bb) {
        float[] fr = {0f, 0.55f, 1f};
        Color[] cols = {new Color(rr, gg, bb, (int) (255 * strength)), new Color(rr, gg, bb, (int) (255 * strength * 0.22f)), new Color(rr, gg, bb, 0)};
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) r, fr, cols));
        g.fillRect((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
    }

    private static void bolt(Graphics2D g, double x1, double y1, double x2, double y2, double wander, int seed, float width, int depth) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 6) return;
        int levels = Math.max(3, Math.min(7, (int) Math.round(Math.log(len / 5.0) / Math.log(2))));
        float[][] p = Lightning.path((float) x1, (float) y1, (float) x2, (float) y2, seed, (float) (len * wander), levels, 0.62f);
        int n = p[0].length;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0][0], p[1][0]);
        for (int i = 1; i < n; i++) path.lineTo(p[0][i], p[1][i]);
        stroke(g, path, width);
        if (depth <= 0) return;
        double ux = (x2 - x1) / len, uy = (y2 - y1) / len;
        for (int k = 0; k < 3; k++) {
            int at = 2 + (int) (Lightning.hash(seed, 100 + k) * Math.max(1, n - 5));
            if (at >= n - 1) continue;
            double side = Lightning.hash(seed, 110 + k) < 0.5 ? -1 : 1;
            double ang = side * (0.35 + 0.7 * Lightning.hash(seed, 120 + k));
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double bdx = ux * ca - uy * sa, bdy = ux * sa + uy * ca;
            double bl = len * (0.15 + 0.22 * Lightning.hash(seed, 130 + k));
            double bx = p[0][at], by = p[1][at];
            bolt(g, bx, by, bx + bdx * bl, by + bdy * bl, wander, seed * 31 + k * 17 + 5, width * 0.6f, depth - 1);
        }
    }

    private static void stroke(Graphics2D g, Path2D.Double path, float width) {
        g.setStroke(new BasicStroke(width * 9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(40, 110, 255, 22));
        g.draw(path);
        g.setStroke(new BasicStroke(width * 4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(60, 140, 255, 70));
        g.draw(path);
        g.setStroke(new BasicStroke(width * 1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(110, 180, 255, 190));
        g.draw(path);
        g.setStroke(new BasicStroke(Math.max(1f, width * 0.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(225, 240, 255, 245));
        g.draw(path);
    }
}
