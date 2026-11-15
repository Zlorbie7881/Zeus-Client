package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

import java.util.IdentityHashMap;

public final class ZeusSkins {
    private ZeusSkins() {}

    private static final IdentityHashMap<AbstractWidget, float[]> STATE = new IdentityHashMap<>();
    private static Screen current;
    private static long last;
    private static final int[] LOGO = new int[4];
    private static Screen logoFor;

    static int kind(Screen s) {
        if (s == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (s instanceof PauseScreen) return UiSettings.PAUSE_STYLE.on() && GreekFont.available() ? 1 : 0;
        if ((s instanceof SelectWorldScreen || s instanceof JoinMultiplayerScreen) && mc != null && mc.level == null) {
            return UiSettings.STONE_LISTS.on() && UiSettings.MENU.on() && GreekFont.available() ? 2 : 0;
        }
        return 0;
    }

    public static boolean hidesTitle(Screen s) {
        return kind(s) == 1 && logoFor == s;
    }

    public static void before(Screen s) {
        try {
            if (kind(s) == 0) return;
            String title = s.getTitle().getString();
            for (var child : s.children()) {
                if (child instanceof StringWidget sw && sw.getMessage().getString().equals(title)) sw.visible = false;
            }
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    public static void after(Screen s, GuiGraphics g, int mx, int my) {
        int kind = kind(s);
        if (kind == 0) return;
        try {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            if (current != s) {
                STATE.clear();
                current = s;
            }
            float t = (now / 1_000_000L % 100_000_000L) / 1000f;
            if (kind == 1) {
                g.fill(0, 0, s.width, s.height, Draw.rgba(0xFF04060C, 0.66f));
                logo(s, g);
            } else {
                title(s, g);
            }
            for (var child : s.children()) {
                if (child instanceof Button b && b.visible) skin(g, b, mx, my, dt, t);
            }
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private static boolean warned;

    private static void warn(Throwable e) {
        if (!warned) Zeus.LOG.warn("The Zeus look on a game screen failed; the game's own look stays", e);
        warned = true;
    }

    public static void pauseInit(Screen s) {
        try {
            if (kind(s) != 1) return;
            int top = Integer.MAX_VALUE;
            int bottom = 0;
            for (var child : s.children()) {
                if (child instanceof Button b) {
                    top = Math.min(top, b.getY());
                    bottom = Math.max(bottom, b.getY() + b.getHeight());
                }
            }
            if (top == Integer.MAX_VALUE) return;
            int h = Math.max(40, Math.min(110, Math.round(s.height * 0.27f)));
            int want = 6 + h + 8;
            int dy = Math.max(0, want - top);
            while (dy > 0 && bottom + dy > s.height - 6 && h > 36) {
                h -= 4;
                want = 6 + h + 8;
                dy = Math.max(0, want - top);
            }
            if (dy > 0 && bottom + dy > s.height - 4) {
                logoFor = null;
                return;
            }
            if (dy > 0) {
                for (var child : s.children()) {
                    if (child instanceof AbstractWidget w) w.setY(w.getY() + dy);
                }
            }
            LOGO[0] = 6;
            LOGO[1] = h;
            logoFor = s;
            column(s, h);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private static void column(Screen s, int logoH) {
        java.util.List<AbstractWidget> buttons = new java.util.ArrayList<>();
        for (var child : s.children()) {
            if (child instanceof Button b && b.visible && b.getWidth() >= 20 && b.getHeight() >= 12) buttons.add(b);
        }
        if (buttons.size() < 2 || s.width < 120) return;
        buttons.sort(java.util.Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));
        int bw = Math.min(240, s.width - 32);
        int gap = 4;
        int total = buttons.size() * 20 + (buttons.size() - 1) * gap;
        int startY = Math.max(logoH + 12, (s.height - total) / 2 + 4);
        if (startY + total > s.height - 8) startY = Math.max(logoH + 8, s.height - 8 - total);
        int y = startY;
        for (AbstractWidget b : buttons) {
            b.setWidth(bw);
            b.setHeight(20);
            b.setX(s.width / 2 - bw / 2);
            b.setY(y);
            y += 20 + gap;
        }
    }

    private static void logo(Screen s, GuiGraphics g) {
        if (logoFor != s) return;
        int h = LOGO[1];
        int w = Math.round(h * 830f / 920f);
        int gs = Tex.guiScale();
        Tex.Img img = Tex.fitted("pause_logo_" + h * gs, "textures/gui/menu/logo.png", w * gs, h * gs, false, 0, 0);
        if (img == null) return;
        int x = s.width / 2 - w / 2;
        Lightning.bloom(g, x + w / 2f, LOGO[0] + h * 0.34f, h * 0.55f, 0xFF7C9CE0, 0.10f);
        img.draw(g, x, LOGO[0], w, h, 0xFFFFFFFF);
    }

    private static void title(Screen s, GuiGraphics g) {
        String title = s.getTitle().getString();
        int cap = 11;
        if (!GreekFont.supports(title)) return;
        int y = 12;
        for (var child : s.children()) {
            if (child instanceof StringWidget sw && sw.getMessage().getString().equals(title)) {
                y = sw.getY() + (sw.getHeight() - cap) / 2;
            }
        }
        GreekFont.drawCentered(g, title, s.width / 2, y, cap, 0xFFE4E8F2, true);
    }

    private static void skin(GuiGraphics g, Button b, int mx, int my, float dt, float t) {
        int x = b.getX();
        int y = b.getY();
        int w = b.getWidth();
        int h = b.getHeight();
        if (w < 20 || h < 12) return;
        float[] st = STATE.computeIfAbsent(b, k -> new float[] {0f, -10f, 0f});
        boolean hot = b.active && (b.isHoveredOrFocused() || (mx >= x && mx < x + w && my >= y && my < y + h));
        float prev = st[0];
        st[0] += ((hot ? 1f : 0f) - st[0]) * (1f - (float) Math.exp(-dt * 12f));
        if (st[0] < 0.003f) st[0] = 0f;
        if (hot && prev < 0.05f) st[1] = t;
        float gl = st[0];
        boolean fx = !UiSettings.CALM.on();
        float u = Math.max(0.8f, h / 20f);
        int tint = b.active ? 0xFFFFFFFF : 0xFF8A8A92;
        if (gl > 0.01f) Draw.glow(g, x, y, w, h, 3, 0xFF7C9CE0, 4, 0.34f * gl);
        Plate9.draw(g, x, y, w, h, tint);
        String label = b.getMessage().getString();
        int capH = Math.max(6, Math.round(h * 0.46f));
        boolean greek = GreekFont.supports(label);
        if (greek) {
            while (capH > 6 && GreekFont.width(label, capH) > w - 12) capH--;
            greek = GreekFont.width(label, capH) <= w - 8;
        }
        int ink = b.active ? Draw.mix(0xFFFFFFFF, 0xFFDCE8FF, gl) : 0xFF8A8A92;
        if (greek) {
            GreekFont.drawCentered(g, label, x + w / 2, y + (h - capH) / 2, capH, ink, true);
            if (gl > 0.02f) GreekFont.drawCentered(g, label, x + w / 2, y + (h - capH) / 2, capH, Draw.rgba(0xFFDCE8FF, 0.45f * gl), false);
        } else {
            Draw.textCenter(g, label, x + w / 2, y + (h - 8) / 2, b.active ? 0xFFE8EEF8 : 0xFF8A8A92);
        }
        if (gl > 0.02f) Draw.rrectLine(g, x, y, w, h, 3, Draw.rgba(0xFFA8BEEA, (0.68f + 0.10f * (float) Math.sin(t * 4f)) * gl));
        if (gl > 0.02f && fx) {
            float since = t - st[1];
            HoverFx.shine(g, x, y, w, h, since / 0.62f, u, gl, SHINE);
            if (!Perf.fast()) PlateFx.hover(g, x, y, w, h, t, gl, u);
            HoverFx.underline(g, x, y, w, h, gl, t);
        }
    }

    private static final int[] SHINE = new int[2];
}
