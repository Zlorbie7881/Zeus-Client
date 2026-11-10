package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Category;
import net.minecraft.client.gui.GuiGraphics;

/** Small vector icons for the pages, drawn from lines so they stay sharp at any size. */
public final class Icons {
    private Icons() {}

    public static final int ACTIVE = 100;
    public static final int PANEL = 101;
    public static final int WINDOWS = 102;
    public static final int GEAR = 103;

    private static void poly(GuiGraphics g, float cx, float cy, float rx, float ry, int n, float thick, int c) {
        float px = cx + rx;
        float py = cy;
        for (int i = 1; i <= n; i++) {
            float a = i / (float) n * 6.2832f;
            float x = cx + (float) Math.cos(a) * rx;
            float y = cy + (float) Math.sin(a) * ry;
            Draw.line(g, px, py, x, y, thick, c);
            px = x;
            py = y;
        }
    }

    public static void draw(GuiGraphics g, Category cat, float cx, float cy, int color) {
        draw(g, cat == null ? -1 : cat.ordinal(), cx, cy, color);
    }

    /** {@code id} is a Category ordinal, or one of ACTIVE, PANEL, WINDOWS, GEAR. */
    public static void draw(GuiGraphics g, int id, float cx, float cy, int c) {
        float t = 1.3f;
        if (id == Category.COMBAT.ordinal()) {
            Draw.line(g, cx - 4, cy + 4, cx + 3.5f, cy - 3.5f, t + 0.3f, c);
            Draw.line(g, cx - 4.5f, cy - 0.5f, cx + 0.5f, cy + 4.5f, t, c);
            Draw.line(g, cx + 3.5f, cy - 3.5f, cx + 5, cy - 5, 1f, c);
        } else if (id == Category.MOVEMENT.ordinal()) {
            Draw.line(g, cx - 4, cy - 4, cx, cy, t, c);
            Draw.line(g, cx, cy, cx - 4, cy + 4, t, c);
            Draw.line(g, cx + 0.5f, cy - 4, cx + 4.5f, cy, t, c);
            Draw.line(g, cx + 4.5f, cy, cx + 0.5f, cy + 4, t, c);
        } else if (id == Category.PLAYER.ordinal()) {
            Draw.ellipse(g, Math.round(cx), Math.round(cy - 2.5f), 2, 2, c);
            Draw.rrect(g, Math.round(cx - 4), Math.round(cy + 1), 8, 4, 2, c);
        } else if (id == Category.WORLD.ordinal()) {
            poly(g, cx, cy, 4.5f, 4.5f, 6, t, c);
            Draw.line(g, cx, cy - 4.5f, cx, cy + 4.5f, 1f, c);
            Draw.line(g, cx - 4.3f, cy - 2.2f, cx, cy + 0.5f, 1f, c);
            Draw.line(g, cx + 4.3f, cy - 2.2f, cx, cy + 0.5f, 1f, c);
        } else if (id == Category.RENDER.ordinal()) {
            Draw.line(g, cx - 5.5f, cy, cx, cy - 3.5f, t, c);
            Draw.line(g, cx, cy - 3.5f, cx + 5.5f, cy, t, c);
            Draw.line(g, cx + 5.5f, cy, cx, cy + 3.5f, t, c);
            Draw.line(g, cx, cy + 3.5f, cx - 5.5f, cy, t, c);
            Draw.ellipse(g, Math.round(cx), Math.round(cy), 2, 2, c);
        } else if (id == Category.SCANNER.ordinal()) {
            poly(g, cx, cy, 5f, 5f, 12, 1.1f, c);
            Draw.line(g, cx, cy, cx + 3.2f, cy - 3.2f, t, c);
            g.fill(Math.round(cx) - 1, Math.round(cy) - 1, Math.round(cx) + 1, Math.round(cy) + 1, c);
        } else if (id == Category.UTILITY.ordinal() || id == GEAR) {
            poly(g, cx, cy, 3f, 3f, 10, 1.1f, c);
            for (int i = 0; i < 4; i++) {
                float a = i * 1.5708f + 0.7854f;
                Draw.line(g, cx + (float) Math.cos(a) * 3.2f, cy + (float) Math.sin(a) * 3.2f, cx + (float) Math.cos(a) * 5.4f, cy + (float) Math.sin(a) * 5.4f, 1.6f, c);
            }
            for (int i = 0; i < 4; i++) {
                float a = i * 1.5708f;
                Draw.line(g, cx + (float) Math.cos(a) * 3.2f, cy + (float) Math.sin(a) * 3.2f, cx + (float) Math.cos(a) * 5.2f, cy + (float) Math.sin(a) * 5.2f, 1.6f, c);
            }
        } else if (id == Category.FRIENDS.ordinal()) {
            int x = Math.round(cx);
            int y = Math.round(cy);
            Draw.ellipse(g, x - 3, y - 3, 2, 2, c);
            Draw.ellipse(g, x + 3, y - 3, 2, 2, c);
            Draw.line(g, x - 6, y + 5, x - 3, y + 1, t, c);
            Draw.line(g, x - 3, y + 1, x, y + 5, t, c);
            Draw.line(g, x, y + 5, x + 3, y + 1, t, c);
            Draw.line(g, x + 3, y + 1, x + 6, y + 5, t, c);
        } else if (id == Category.PROFILES.ordinal()) {
            int x = Math.round(cx);
            int y = Math.round(cy);
            Draw.rrectLine(g, x - 5, y - 6, 10, 12, 2, c);
            g.fill(x - 3, y - 3, x + 3, y - 2, c);
            g.fill(x - 3, y, x + 3, y + 1, c);
            g.fill(x - 3, y + 3, x + 1, y + 4, c);
        } else if (id == Category.PANELS.ordinal()) {
            int x = Math.round(cx);
            int y = Math.round(cy);
            Draw.rrectLine(g, x - 6, y - 5, 12, 10, 2, c);
            g.fill(x - 6, y - 2, x + 6, y - 1, c);
            g.fill(x - 4, y + 1, x + 1, y + 2, c);
            g.fill(x - 4, y + 3, x + 3, y + 4, c);
        } else if (id == Category.INTERFACE.ordinal() || id == WINDOWS) {
            int x = Math.round(cx);
            int y = Math.round(cy);
            Draw.rrect(g, x - 5, y - 5, 4, 4, 1, c);
            Draw.rrect(g, x + 1, y - 5, 4, 4, 1, c);
            Draw.rrect(g, x - 5, y + 1, 4, 4, 1, c);
            Draw.rrect(g, x + 1, y + 1, 4, 4, 1, c);
        } else if (id == ACTIVE) {
            Draw.line(g, cx + 1.5f, cy - 5.5f, cx - 2.5f, cy + 0.5f, t + 0.2f, c);
            Draw.line(g, cx - 2.5f, cy + 0.5f, cx + 2, cy + 0.5f, t + 0.2f, c);
            Draw.line(g, cx + 2, cy + 0.5f, cx - 1.5f, cy + 5.5f, t + 0.2f, c);
        } else if (id == PANEL) {
            int x = Math.round(cx);
            int y = Math.round(cy);
            g.fill(x - 5, y - 3, x + 5, y - 2, c);
            g.fill(x - 5, y + 2, x + 5, y + 3, c);
            Draw.ellipse(g, x - 2, y - 3, 2, 2, c);
            Draw.ellipse(g, x + 2, y + 2, 2, 2, c);
        }
    }
}
