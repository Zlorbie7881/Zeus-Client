package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The stone plate of the menu drawn at any width and height: the two ends keep their shape and the middle stretches, so a
 * long bar and a short one both look like the same cut stone.
 */
public final class Plate9 {
    private Plate9() {}

    private static final String PICTURE = "textures/gui/menu/plate.png";
    private static final int SRC_W = 764;
    private static final int SRC_H = 108;
    private static final int CAP = 74;

    public static void draw(GuiGraphics g, int x, int y, int w, int h, int tint) {
        if (w < 8 || h < 4) return;
        int gs = Tex.guiScale();
        float k = h * gs / (float) SRC_H;
        int tw = Math.max(2, Math.round(SRC_W * k));
        int th = Math.max(1, h * gs);
        Tex.Img img = Tex.fitted("plate9_" + th, PICTURE, tw, th, false, 0, 0);
        if (img == null) {
            Draw.rrect(g, x, y, w, h, 3, tint);
            return;
        }
        int capTex = Math.round(CAP * k);
        int capDst = Math.max(2, Math.round(capTex / (float) gs));
        if (capDst * 2 >= w) {
            img.draw(g, x, y, w, h, tint);
            return;
        }
        img.drawPart(g, x, y, capDst, h, 0, 0, capTex, th, tint);
        img.drawPart(g, x + w - capDst, y, capDst, h, tw - capTex, 0, capTex, th, tint);
        img.drawPart(g, x + capDst, y, w - 2 * capDst, h, capTex, 0, tw - 2 * capTex, th, tint);
    }
}
