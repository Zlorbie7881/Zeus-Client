package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** The smaller pieces of the Zeus menu: Continue card, update notice, splash line, chevrons, shooting stars. */
final class MenuExtras {
    private MenuExtras() {
    }

    private static final String[] SPLASHES = {
            "72 knives!", "Now with crates!", "Struck by lightning!", "Type @ to ask Zeus!", "Olympian drop rate: 1%!",
            "Thunder approves!", "Hades sends his regards!", "Storms ahead!", "Fabric 1.21.11!", "Bloodstone!",
            "Open a crate!", "Ask the AI to build it!", "Spin the karambit!", "Made for Olympus!", "Try Quick open!",
            "Lightning never strikes twice. Zeus does.", "Collect them all!", "Inspect your knife!", "Gold leaf!",
            "Now 100% more thunder!"};
    private static final String SPLASH = SPLASHES[(int) (Math.random() * SPLASHES.length)];

    private static float hotContinue;
    private static float hotUpdate;

    static float clamp01(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    /** Silver ink and the menu's blue hover light, as on the stone plates. */
    private static final int INK = 0xFFE8EEF8;
    private static final int INK_DIM = 0xFF9AA8C8;
    private static final int HOVER = 0xFF7C9CE0;
    private static final int HOVER_LINE = 0xFFA8BEEA;
    private static final int[] SHINE = new int[2];
    private static float contEnter = -10f;
    private static boolean contWasHot;

    /** Greek capitals when the menu font has every letter, otherwise the normal bold font. */
    private static void title(GuiGraphics g, String s, int cx, int y, int cap, int tint) {
        if (GreekFont.supports(s)) GreekFont.drawCentered(g, s, cx, y, cap, tint, true);
        else Draw.boldCenter(g, s, cx, y + (cap - 8) / 2, tint);
    }

    /**
     * The "jump back in" card: a stone plate like the menu buttons, with the last world or server and
     * how long ago, one click to play. Returns its box {x, y, w, h}, or null when there is nothing.
     */
    static int[] continueCard(GuiGraphics g, int right, int y, int h, float u, int mx, int my, float t, float dt) {
        LastPlayed.Entry e = LastPlayed.get();
        if (e == null) return null;
        float appear = clamp01((t - 0.75f) / 0.5f);
        if (appear <= 0f) return null;
        float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
        int w = Math.max(120, Math.round(300 * u));
        int x = right - w - Math.round((1f - ease) * 14f);
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        if (hot && !contWasHot) contEnter = t;
        contWasHot = hot;
        hotContinue += ((hot ? 1f : 0f) - hotContinue) * (1f - (float) Math.exp(-dt * 12f));
        float hv = hotContinue;
        int lift = Math.round(1.5f * u * hv);
        if (hv > 0.02f) Draw.shadow(g, x, y, w, h, 3, Math.max(3, Math.round(5 * u * hv)), 0.4f * hv * ease);
        if (hv > 0.01f) Draw.glow(g, x, y - lift, w, h, 3, HOVER, Math.max(3, Math.round(6 * u)), 0.34f * hv * ease);
        Plate9.draw(g, x, y - lift, w, h, Draw.rgba(0xFFFFFFFF, ease));
        // three rows: CONTINUE in the menu's lettering, the world, when
        int cap = Math.max(6, Math.round(h * 0.24f));
        int nameY = y - lift + Math.round(h * 0.18f) + cap + Math.max(3, Math.round(h * 0.06f));
        title(g, "CONTINUE", x + w / 2, y - lift + Math.round(h * 0.16f), cap, Draw.rgba(Draw.mix(0xFFD8DEE8, 0xFFFFFFFF, hv), ease));
        if (hv > 0.02f) title(g, "CONTINUE", x + w / 2, y - lift + Math.round(h * 0.16f), cap, Draw.rgba(0xFFDCE8FF, 0.45f * hv * ease));
        int pad = Math.max(8, Math.round(w * 0.09f));
        Draw.textCenter(g, Draw.fit(e.name, w - pad * 2), x + w / 2, nameY, Draw.rgba(INK, ease));
        String sub = (e.server ? "Server" : "World") + "  \u00b7  " + LastPlayed.ago(e.at);
        Draw.textCenter(g, Draw.fit(sub, w - pad * 2), x + w / 2, nameY + 11, Draw.rgba(INK_DIM, ease));
        if (hv > 0.02f) {
            Draw.rrectLine(g, x, y - lift, w, h, 3, Draw.rgba(HOVER_LINE, (0.68f + 0.10f * (float) Math.sin(t * 4f)) * hv * ease));
            HoverFx.shine(g, x, y - lift, w, h, (t - contEnter) / 0.62f, u, hv * ease, SHINE);
        }
        return new int[] {x, y, w, h};
    }

    /** A small stone plate top-left when a newer Zeus build is out. Returns its box or null. */
    static int[] updatePill(GuiGraphics g, int mx, int my, float t, float dt) {
        int build = MenuOnline.updateAvailable();
        if (build <= 0) return null;
        float appear = clamp01((t - 1.2f) / 0.5f);
        if (appear <= 0) return null;
        int cap = 7;
        String s = "UPDATE";
        int tw = GreekFont.supports(s) ? GreekFont.width(s, cap) : Draw.widthBold(s);
        int w = tw + 34, h = 18;
        int x = 8, y = 8 - Math.round((1f - appear) * 10f);
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        hotUpdate += ((hot ? 1f : 0f) - hotUpdate) * (1f - (float) Math.exp(-dt * 12f));
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6f);
        Draw.glow(g, x, y, w, h, 3, HOVER, 6, (0.10f + 0.10f * pulse + 0.25f * hotUpdate) * appear);
        Plate9.draw(g, x, y, w, h, Draw.rgba(0xFFFFFFFF, appear));
        // a silver up arrow
        int ax = x + 9, ay = y + 5, ink = Draw.rgba(Draw.mix(0xFFC8D4E8, 0xFFFFFFFF, hotUpdate), appear);
        for (int k = 0; k < 4; k++) g.fill(ax + 3 - k, ay + k, ax + 4 + k, ay + k + 1, ink);
        g.fill(ax + 2, ay + 4, ax + 5, ay + 9, ink);
        title(g, s, x + 21 + tw / 2, y + (h - cap) / 2, cap, ink);
        if (hotUpdate > 0.02f) Draw.rrectLine(g, x, y, w, h, 3, Draw.rgba(HOVER_LINE, 0.7f * hotUpdate * appear));
        if (hot) {
            String tip = "Build " + build + " is out. Click to download it.";
            Draw.rrect(g, x, y + h + 3, Draw.width(tip) + 10, 13, 4, 0xF0050914);
            Draw.rrectLine(g, x, y + h + 3, Draw.width(tip) + 10, 13, 4, 0xFF2A4A8A);
            Draw.text(g, tip, x + 5, y + h + 6, INK);
        }
        return new int[] {x, y, w, h};
    }

    /** The number of crates waiting, on a small stone plate beside the Zeus Crates button. */
    static void crateCount(GuiGraphics g, int x, int y, int h, int n, float ease, float t) {
        String s = n > 99 ? "99" : String.valueOf(n);
        int cap = Math.max(5, Math.round(h * 0.34f));
        int tw = GreekFont.supports(s) ? GreekFont.width(s, cap) : Draw.widthBold(s);
        int bh = Math.max(12, Math.round(h * 0.70f)), bw = Math.max(bh, tw + 14);
        int by = y + (h - bh) / 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);
        Draw.glow(g, x, by, bw, bh, 3, HOVER, 5, (0.10f + 0.12f * pulse) * ease);
        Plate9.draw(g, x, by, bw, bh, Draw.rgba(0xFFFFFFFF, ease));
        title(g, s, x + bw / 2, by + (bh - cap) / 2, cap, Draw.rgba(INK, ease));
    }

    /** The splash line beside the logo, tilted and pulsing like vanilla's, in the menu's pale blue. */
    static void splash(GuiGraphics g, float x, float y, float u, float t) {
        float appear = clamp01((t - 1.0f) / 0.4f);
        if (appear <= 0) return;
        float pulse = 1f + 0.06f * (float) Math.abs(Math.sin(t * 6.0f));
        float base = Math.max(0.7f, Math.min(1.6f, u * 2.4f));
        int len = Draw.widthBold(SPLASH);
        float sc = base * pulse * Math.min(1f, 120f / Math.max(1, len) + 0.35f);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate(-0.35f);
        g.pose().scale(sc * (0.6f + 0.4f * appear), sc * (0.6f + 0.4f * appear));
        Draw.boldCenter(g, SPLASH, 1, -3, Draw.rgba(0xFF0A1226, appear));
        Draw.boldCenter(g, SPLASH, 0, -4, Draw.rgba(0xFFBFD6FF, appear));
        g.pose().popMatrix();
    }

    /** Small chevrons that slide in either side of a hovered menu button. */
    static void chevrons(GuiGraphics g, int x, int y, int w, int h, float gl, float u, int colour) {
        if (gl <= 0.02f) return;
        int cy = y + h / 2;
        int size = Math.max(3, Math.round(h * 0.18f));
        float slide = (1f - gl) * 10f * u;
        int lx = Math.round(x + w * 0.07f - slide), rx = Math.round(x + w * 0.93f + slide);
        int col = Draw.rgba(colour, gl);
        for (int k = 0; k < 2; k++) {
            int off = k * (size + 2);
            Draw.line(g, lx + off, cy - size, lx + off + size, cy, 2f, col);
            Draw.line(g, lx + off + size, cy, lx + off, cy + size, 2f, col);
            Draw.line(g, rx - off, cy - size, rx - off - size, cy, 2f, col);
            Draw.line(g, rx - off - size, cy, rx - off, cy + size, 2f, col);
        }
    }

    /** Now and then a shooting star crosses the sky (not in a storm, not in calm mode). */
    static void shootingStars(GuiGraphics g, float[] back, float t, float storm) {
        if (storm > 0.3f) return;
        float period = 6.5f;
        int cycle = (int) (t / period);
        float p = (t - cycle * period) / 0.85f;
        if (p < 0f || p > 1f) return;
        float h1 = Lightning.hash(cycle, 91), h2 = Lightning.hash(cycle, 92), h3 = Lightning.hash(cycle, 93);
        if (h3 < 0.25f) return;                       // some cycles stay empty
        float cs = back[0];
        float sx = back[1] + (300f + h1 * 800f) * cs, sy = back[2] + (20f + h2 * 140f) * cs;
        float len = 160f * cs, dx = -0.86f, dy = 0.5f;
        float head = p * len * 1.6f;
        float fade = p < 0.15f ? p / 0.15f : 1f - (p - 0.15f) / 0.85f;
        for (int k = 0; k < 8; k++) {
            float a0 = head - k * len * 0.07f, a1 = head - (k + 1) * len * 0.07f;
            if (a1 < 0) break;
            Draw.line(g, sx + dx * a0, sy + dy * a0, sx + dx * a1, sy + dy * a1, k == 0 ? 2f : 1f,
                    Draw.rgba(0xFFDCE8FF, fade * (1f - k / 8f) * 0.9f));
        }
    }

    static String splashRaw() {
        return SPLASH;
    }

    static String splashText() {
        return SPLASH.toLowerCase(Locale.ROOT);
    }
}
