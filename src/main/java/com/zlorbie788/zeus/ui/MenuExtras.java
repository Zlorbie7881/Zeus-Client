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

    /**
     * The "jump back in" card: last world or server, how long ago, one click to play.
     * Returns its box {x, y, w, h}, or null when there is nothing to continue.
     */
    static int[] continueCard(GuiGraphics g, int right, int y, int h, float u, int mx, int my, float t, float dt) {
        LastPlayed.Entry e = LastPlayed.get();
        if (e == null) return null;
        float appear = clamp01((t - 0.75f) / 0.5f);
        if (appear <= 0f) return null;
        float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
        int w = Math.max(112, Math.round(280 * u));
        int x = right - w - Math.round((1f - ease) * 18f);
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        hotContinue += ((hot ? 1f : 0f) - hotContinue) * (1f - (float) Math.exp(-dt * 12f));
        float hv = hotContinue;
        int dx = Math.round(hv * 2f);
        Draw.glow(g, x + dx, y, w, h, 6, 0xFF7C9CE0, Math.max(4, Math.round(8 * u)), (0.06f + 0.22f * hv) * ease);
        Draw.rrect(g, x + dx, y, w, h, 6, Draw.rgba(Draw.mix(0xFF0A1428, 0xFF13234A, hv), 0.86f * ease), Draw.rgba(0xFF050914, 0.86f * ease));
        Draw.rrectLine(g, x + dx, y, w, h, 6, Draw.rgba(Draw.mix(0xFF2A4A8A, 0xFF8EB4FF, hv), ease));
        g.fill(x + dx + 1, y + 4, x + dx + 3, y + h - 4, Draw.rgba(0xFFF5C04A, ease));
        int pad = Math.max(5, Math.round(10 * u));
        int lh = Math.max(9, (h - 6) / 3);
        int ty = y + (h - lh * 3) / 2 + 1;
        // play triangle, breathing
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        int tri = 6;
        int tx = x + dx + pad;
        for (int k = 0; k < tri; k++) g.fill(tx + k, ty + k / 2, tx + k + 1, ty + tri - k / 2, Draw.rgba(Draw.mix(0xFFF5C04A, 0xFFFFE6A8, pulse * hv), ease));
        Draw.bold(g, "CONTINUE", tx + tri + 4, ty, Draw.rgba(0xFFF5C04A, ease));
        Draw.bold(g, Draw.fit(e.name, w - pad * 2), x + dx + pad, ty + lh, Draw.rgba(0xFFE9F1FF, ease));
        String sub = (e.server ? "Server" : "World") + "  ·  " + LastPlayed.ago(e.at);
        Draw.text(g, Draw.fit(sub, w - pad * 2), x + dx + pad, ty + lh * 2, Draw.rgba(0xFF8EA2CC, ease));
        return new int[] {x, y, w, h};
    }

    /** A gold pill in the top-left corner when a newer Zeus build is out. Returns its box or null. */
    static int[] updatePill(GuiGraphics g, int mx, int my, float t, float dt) {
        int build = MenuOnline.updateAvailable();
        if (build <= 0) return null;
        float appear = clamp01((t - 1.2f) / 0.5f);
        if (appear <= 0) return null;
        String s = "NEW UPDATE";
        int w = Draw.widthBold(s) + 28, h = 16;
        int x = 8, y = 8 - Math.round((1f - appear) * 10f);
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        hotUpdate += ((hot ? 1f : 0f) - hotUpdate) * (1f - (float) Math.exp(-dt * 12f));
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        Draw.glow(g, x, y, w, h, 8, 0xFFF5C04A, 8, (0.12f + 0.10f * pulse + 0.2f * hotUpdate) * appear);
        Draw.rrect(g, x, y, w, h, 8, Draw.rgba(Draw.lighten(0xFFF5C04A, 0.1f * hotUpdate), appear), Draw.rgba(0xFFC8901E, appear));
        // an up arrow
        int ax = x + 8, ay = y + 4;
        for (int k = 0; k < 4; k++) g.fill(ax + 3 - k, ay + k, ax + 4 + k, ay + k + 1, Draw.rgba(0xFF1A1206, appear));
        g.fill(ax + 2, ay + 4, ax + 5, ay + 8, Draw.rgba(0xFF1A1206, appear));
        Draw.bold(g, s, x + 18, y + 4, Draw.rgba(0xFF1A1206, appear));
        if (hot) {
            String tip = "Build " + build + " is out. Click to download it.";
            Draw.rrect(g, x, y + h + 3, Draw.width(tip) + 10, 13, 4, 0xF0050914);
            Draw.text(g, tip, x + 5, y + h + 6, 0xFFE9F1FF);
        }
        return new int[] {x, y, w, h};
    }

    /** The yellow splash line beside the logo, tilted and pulsing like vanilla's. */
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
        Draw.boldCenter(g, SPLASH, 1, -3, Draw.rgba(0xFF3F3F15, appear));
        Draw.boldCenter(g, SPLASH, 0, -4, Draw.rgba(0xFFFFFF55, appear));
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
