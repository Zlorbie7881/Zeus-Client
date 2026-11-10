package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The date and the real time, with seconds, in the top right corner of the menu. */
final class MenuClock {
    private MenuClock() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_24 = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_12 = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);

    static void draw(GuiGraphics g, int width, int height, float u, float appear) {
        if (!UiSettings.MENU_CLOCK.on() || appear <= 0.01f) return;
        LocalDateTime now = LocalDateTime.now();
        String time = (UiSettings.CLOCK_24H.on() ? TIME_24 : TIME_12).format(now);
        String date = DATE.format(now);
        float ts = Math.max(1f, Math.round(u * 2.2f * 2f) / 2f);
        float ds = Math.max(1f, Math.round(u * 1.05f * 2f) / 2f);
        int padX = Math.max(8, Math.round(11 * u));
        int padY = Math.max(5, Math.round(7 * u));
        int tw = Draw.widthS(time, ts);
        int dw = Draw.widthS(date, ds);
        int th = Math.round(8 * ts);
        int dh = Math.round(8 * ds);
        int gap = Math.max(3, Math.round(4 * u));
        int w = Math.max(tw, dw) + padX * 2;
        int h = padY * 2 + th + gap + dh;
        int x = width - Math.max(8, Math.round(18 * u)) - w;
        int y = Math.max(6, Math.round(14 * u)) - Math.round((1f - appear) * 8f);
        float a = appear;
        Draw.rrect(g, x, y, w, h, 4, Draw.rgba(0xFF050914, 0.62f * a), Draw.rgba(0xFF03060E, 0.62f * a));
        Draw.rrectLine(g, x, y, w, h, 4, Draw.rgba(0xFF5A6C98, 0.80f * a));
        int xr = x + w - padX;
        // the time, with a soft glow, and the date under it with a thin rule between
        Draw.textRightS(g, time, xr + 1, y + padY + 1, Draw.rgba(0xFF000000, 0.55f * a), ts);
        Draw.textRightS(g, time, xr, y + padY, Draw.rgba(0xFFEAF0FF, a), ts);
        int ry = y + padY + th + gap / 2;
        g.fill(x + padX, ry, x + w - padX, ry + 1, Draw.rgba(0xFF8CA0D0, 0.35f * a));
        Draw.textRightS(g, date, xr, y + padY + th + gap, Draw.rgba(0xFFB4C0DC, 0.95f * a), ds);
    }
}
