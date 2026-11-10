package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * How the medallion on the menu reacts to a lightning strike.
 *
 * <ul>
 *   <li><b>It is lit from the side of the strike.</b> The picture is lit in a grid of small pieces, and the pieces facing the
 *       strike take the light most, so a bolt on the left lights the left of the face and leaves the right in shade.</li>
 *   <li><b>An arc leaps</b> from the rim, on the side of the strike, out towards the bolt, flickering with the flash.</li>
 *   <li><b>A shock ring</b> leaves the rim at the peak of the flash and spreads.</li>
 *   <li><b>Sparks crawl round the rim</b> while the flash lasts and a little longer, fading with it.</li>
 * </ul>
 */
final class MedallionFx {
    private MedallionFx() {}

    private static final int COLS = 6;
    private static final int ROWS = 6;

    static void draw(GuiGraphics g, float cx, float cy, float r, float u, float t, float appear, Tex.Img logo, int lx, int ly, int lw, int lh) {
        if (UiSettings.CALM.on() || Perf.fast()) return;
        float k = Math.max(0.7f, u);
        float fl = ZeusTitleScreen.lastFlash;
        float e = MenuStorm.sinceStrike();
        boolean afterglow = e >= 0f && e < 2.2f;
        if (fl <= 0.03f && !afterglow) return;

        // which way the light came from
        float dx = ZeusTitleScreen.flashX - cx;
        float dy = ZeusTitleScreen.flashY - cy;
        float dl = (float) Math.sqrt(dx * dx + dy * dy);
        if (dl < 1f) {
            dx = 0f;
            dy = -1f;
        } else {
            dx /= dl;
            dy /= dl;
        }

        if (fl > 0.03f) {
            // the whole picture lit, a grid of pieces, the ones facing the strike the most
            int sw = logo.w;
            int sh = logo.h;
            for (int j = 0; j < ROWS; j++) {
                int y0 = ly + Math.round(j * lh / (float) ROWS);
                int y1 = ly + Math.round((j + 1) * lh / (float) ROWS);
                int sy0 = Math.round(j * sh / (float) ROWS);
                int sy1 = Math.round((j + 1) * sh / (float) ROWS);
                for (int i = 0; i < COLS; i++) {
                    int x0 = lx + Math.round(i * lw / (float) COLS);
                    int x1 = lx + Math.round((i + 1) * lw / (float) COLS);
                    int sx0 = Math.round(i * sw / (float) COLS);
                    int sx1 = Math.round((i + 1) * sw / (float) COLS);
                    float px = (x0 + x1) / 2f - cx;
                    float py = (y0 + y1) / 2f - cy;
                    float pl = Math.max(1f, (float) Math.sqrt(px * px + py * py));
                    float facing = 0.5f + 0.5f * Math.max(-1f, Math.min(1f, (px * dx + py * dy) / Math.max(r * 0.9f, pl)));
                    float a = fl * appear * (0.10f + 0.58f * facing * facing);
                    if (a > 0.01f) logo.drawPart(g, x0, y0, x1 - x0, y1 - y0, sx0, sy0, sx1 - sx0, sy1 - sy0, Draw.rgba(0xFFC4DAFF, a));
                }
            }
            // the light on the side of the strike spills over the rim
            Lightning.bloom(g, cx + dx * r * 0.55f, cy + dy * r * 0.55f, r * 0.95f, 0xFFB4D2FF, 0.26f * fl * appear);
            Lightning.bloom(g, cx, cy, r * 1.3f, 0xFFB4D2FF, 0.18f * fl * appear);
            Thunder.ring(g, cx, cy, r * 0.99f, t, fl * appear, k);
            Thunder.ring(g, cx, cy, r * 0.99f, t + 0.37f, fl * appear * 0.8f, k);
        }

        if (e >= 0.05f && e < 0.55f && fl > 0.2f) {
            // an arc leaps from the rim towards the bolt, redrawn from new noise a dozen times a second
            int slot = (int) (t * 12f);
            float fx = cx + dx * r;
            float fy = cy + dy * r;
            float sway = (Lightning.hash(slot, MenuStorm.strikeId() + 3) - 0.5f) * 0.9f;
            float ex = fx + (dx - dy * sway) * r * 1.05f;
            float ey = fy + (dy + dx * sway) * r * 1.05f;
            Lightning.bolt(g, fx, fy, ex, ey, 1f, slot * 13 + MenuStorm.strikeId(), Math.min(1f, fl * 1.1f) * appear, 0xFF8EC8FF, 0xFFD6E6FF, Math.max(1.1f, 1.3f * k), 2, 0.7f, 0f, 3);
            Lightning.bloom(g, fx, fy, r * 0.22f, 0xFFD2E4FF, 0.7f * fl * appear);
        }

        if (e >= 0.10f && e < 0.95f) {
            // the shock ring leaves the rim
            float q = (e - 0.10f) / 0.85f;
            float rad = r * (1.0f + 0.75f * (1f - (1f - q) * (1f - q)));
            float a = (1f - q) * (1f - q) * 0.55f * appear;
            HoverFx.ring(g, cx, cy, rad, rad, Math.max(1f, 2.2f * k * (1f - q)), Draw.rgba(0xFFBCD6FF, a), 56);
            HoverFx.ring(g, cx, cy, rad * 0.93f, rad * 0.93f, 1f, Draw.rgba(0xFFFFFFFF, a * 0.5f), 48);
        }

        if (afterglow && e > 0.25f) {
            // little sparks go on crawling round the rim as the light dies
            float f = (float) Math.pow(1f - (e - 0.25f) / 1.95f, 1.6);
            Thunder.ring(g, cx, cy, r * 0.99f, t * 0.8f + 4f, f * 0.55f * appear, k);
            Lightning.bloom(g, cx, cy, r * 1.15f, 0xFF9CBCFF, 0.10f * f * appear);
        }
    }
}
