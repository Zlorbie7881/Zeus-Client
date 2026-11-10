package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The 3D news scroll in the bottom right corner of the Zeus menu. Closed, it is a rolled-up parchment with bronze knobs that turns
 * slowly on the spot (seven pictures of the same roll seen from slightly different sides, blended into each other); a click unrolls
 * it, and a click on the top rod rolls it up again.
 *
 * <p>The unrolling is a real motion and not a fade: the roll rises while the bottom rod drops out of it on a spring (it swings a
 * little past its place and settles back), the roll gets thinner as the paper leaves it (sixteen ray cast pictures,
 * tools/make_scroll_unroll.py), its bands fall away, the paper ripples while it moves, and the title band is written in at the
 * end. Rolling up runs the same motion without the swing.</p>
 * Open, it is a long parchment with the news written on it: the top rod with the
 * title, the body (much taller than the screen, so a window of it is moved over the sheet) and the bottom rod. The wheel moves the
 * text while the pointer is over the scroll.
 *
 * <p>All pictures are made by {@code tools/make_news_scroll.py}, which ray casts the rods and the roll, so the shading is real
 * depth and not a drawn-on gradient. The pictures are built once at the full size and drawn smaller while the scroll opens, so
 * no picture is made in the middle of the animation.
 */
final class MenuNews {
    // sizes of the pictures, in picture pixels (printed by the generator)
    private static final float SRC_W = 580f;
    private static final float HEAD_H = 104f;
    private static final float FOOT_H = 80f;
    private static final float BODY_H = 691f;
    /** The head and the foot fade out into the body over this many picture pixels, and the body goes under the fade. */
    private static final float FADE = 16f;
    /** How much of the top picture is the rod and the title band (the click area). */
    private static final float HEAD_CLICK = 96f;
    /** The roll is drawn from this many views; 3 is the one looking straight at it. */
    private static final int VIEWS = 7;
    /** The unrolling pictures: this many frames one under the other, each as big as the head picture. */
    private static final int FRAMES = 16;
    /** Where the middle of the top rod is in the head and in each frame; and of the bottom rod in the foot picture. */
    private static final float HEAD_ROD = 24f;
    private static final float FOOT_ROD = 52f;
    /** The roll in the first and in the last frame: its radius and the height of its middle in the frame. */
    private static final float ROLL_R0 = 31f;
    private static final float ROLL_R1 = 13f;
    private static final float ROLL_CY0 = 36f;
    /** Where the middle of the closed roll is in the closed roll picture. */
    private static final float CLOSED_CY = 96f;

    private int x;
    /** Width on screen right now. */
    private int w;
    /** The middle of the top rod (or of the closed roll) and of the bottom rod, on screen. */
    private float topC;
    private float footC;
    private float bottomLine;
    /** Where the opening is (0 closed, 1 open; it swings a little past 1) and how fast it moves. */
    private float openVel;
    /** Width of the open scroll and of the closed roll, in gui pixels. */
    private int wOpen;
    private int wClosed;
    private float s;
    private boolean laidOut;
    private boolean rolled = true;
    private float open;
    private float scroll;
    private float scrollTo;
    private float bodyViewMax;
    private boolean hot;
    private float yaw = 3f;
    private int screenW;
    private int screenH;
    private int bottomMargin;
    private int rightMargin;

    /** Works out how big the scroll can be; returns false when the window is too small for it. */
    boolean layout(int width, int height, int leftLimit, int bottomMargin, int rightMargin) {
        screenW = width;
        screenH = height;
        this.bottomMargin = bottomMargin;
        this.rightMargin = rightMargin;
        wOpen = Math.max(120, Math.min(250, Math.round(width * 0.25f)));
        wClosed = Math.max(80, Math.min(190, Math.round(width * 0.17f)));
        laidOut = width - rightMargin - wOpen >= leftLimit + 8 && height > 120;
        place();
        return laidOut;
    }

    /** Sets the size and the place for the current state of opening. */
    private void place() {
        float e = Math.max(0f, Math.min(1f, open));
        float se = e * e * (3f - 2f * e);
        w = Math.round(wClosed + (wOpen - wClosed) * se);
        s = w / SRC_W;
        float head = HEAD_H * s;
        float foot = FOOT_H * s;
        float space = screenH * 0.62f - head - foot;
        bodyViewMax = Math.max(40f * s, Math.min(BODY_H * s * 0.62f, space));
        x = screenW - rightMargin - w;
        bottomLine = screenH - bottomMargin;
        float y0 = bottomLine - (FOOT_H + HEAD_H - CLOSED_CY) * s;                 // the closed roll
        float dFull = (HEAD_H + FOOT_H - HEAD_ROD - FOOT_ROD + (FOOT_ROD - 28f)) * s + bodyViewMax;
        float y1 = bottomLine - 28f * s - dFull;                                   // the top rod, open
        topC = y0 + (y1 - y0) * e;
        footC = topC + Math.max(0f, open) * dFull;
    }

    /** Builds every picture at the size they will be drawn, so nothing is made in the middle of an animation. */
    void prepare() {
        if (!laidOut) return;
        pictures();
    }

    private Tex.Img[] pictures() {
        int gs = Tex.guiScale();
        float so = wOpen / SRC_W;
        int fw = wOpen * gs;
        Tex.Img[] out = new Tex.Img[4 + VIEWS];
        out[0] = Tex.fitted("news_head", "textures/gui/menu/scroll_head.png", fw, Math.round(HEAD_H * so * gs), false, 0, 0);
        out[1] = Tex.fitted("news_body", "textures/gui/menu/scroll_body.png", fw, Math.round(BODY_H * so * gs), false, 0, 0);
        out[2] = Tex.fitted("news_foot", "textures/gui/menu/scroll_foot.png", fw, Math.round(FOOT_H * so * gs), false, 0, 0);
        out[3 + VIEWS] = Tex.fitted("news_unroll", "textures/gui/menu/scroll_unroll.png", fw, Math.round(HEAD_H * FRAMES * so * gs), false, 0, 0);
        for (int i = 0; i < VIEWS; i++) {
            out[3 + i] = Tex.fitted("news_roll_" + i, "textures/gui/menu/scroll_roll_" + i + ".png", fw, Math.round((HEAD_H + FOOT_H) * so * gs), false, 0, 0);
        }
        return out;
    }

    boolean contains(double mx, double my) {
        if (!laidOut) return false;
        float top = topC - (rolled && open < 0.02f ? CLOSED_CY * 0.7f : 40f) * s;
        float bottom = Math.max(footC + 30f * s, bottomLine);
        return mx >= x - 3 && mx < x + w + 3 && my >= top && my < bottom + 4;
    }

    private boolean onHeader(double mx, double my) {
        if (rolled) return contains(mx, my);
        return laidOut && mx >= x - 3 && mx < x + w + 3 && my >= topC - 40f * s && my < topC + (HEAD_CLICK - HEAD_ROD) * s + 2;
    }

    /** A click: returns true if the scroll used it. */
    boolean click(double mx, double my) {
        if (!contains(mx, my)) return false;
        if (onHeader(mx, my)) rolled = !rolled;
        return true;
    }

    private float maxScroll() {
        return Math.max(0f, BODY_H - (bodyViewMax + 2f * FADE * s) / s);
    }

    boolean wheel(double mx, double my, double amount) {
        if (!contains(mx, my) || rolled || open < 0.9f || Math.abs(openVel) > 0.6f) return false;
        scrollTo = Math.max(0f, Math.min(maxScroll(), scrollTo - (float) amount * 70f));
        return true;
    }

    /** Moves the opening on its spring: a little swing past the end when it opens, none when it rolls up. */
    private void step(float dt) {
        float goal = rolled ? 0f : 1f;
        float k = rolled ? 85f : 52f;
        float zeta = rolled ? 1.0f : 0.56f;
        float c = 2f * (float) Math.sqrt(k) * zeta;
        float rest = Math.min(dt, 0.05f);
        int n = 4;
        float h = rest / n;
        for (int i = 0; i < n; i++) {
            float acc = k * (goal - open) - c * openVel;
            openVel += acc * h;
            open += openVel * h;
            if (open < 0f) {
                open = 0f;
                openVel = Math.max(0f, openVel);
            }
        }
        if (Math.abs(goal - open) < 0.0008f && Math.abs(openVel) < 0.01f) {
            open = goal;
            openVel = 0f;
        }
    }

    private static float smooth(float a, float b, float v) {
        float q = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return q * q * (3f - 2f * q);
    }

    void draw(GuiGraphics g, int mx, int my, float dt, float appear, float t) {
        if (!laidOut || appear <= 0.01f) return;
        step(dt);
        place();
        scrollTo = Math.min(scrollTo, maxScroll());
        scroll += (scrollTo - scroll) * Math.min(1f, dt * 12f);
        if (Math.abs(scrollTo - scroll) < 0.05f) scroll = scrollTo;

        Tex.Img[] p = pictures();
        for (Tex.Img i : p) if (i == null) return;
        Tex.Img unroll = p[3 + VIEWS];
        int px = x + Math.round((1f - appear) * 30f);
        hot = onHeader(mx, my);

        float e = Math.max(0f, Math.min(1f, open));
        // closed, the roll floats a little
        float bob = (1f - smooth(0f, 0.15f, e)) * 1.4f * s * (float) Math.sin(t * 1.3f);
        float tc = topC + bob;
        float fc = footC + bob;

        int fd = Math.round(FADE * s);
        float rollR = (float) Math.sqrt(ROLL_R0 * ROLL_R0 + (ROLL_R1 * ROLL_R1 - ROLL_R0 * ROLL_R0) * e);
        float frameCy = ROLL_CY0 + (HEAD_ROD - ROLL_CY0) * e;
        float yT = tc - frameCy * s;                       // top of the frame picture
        int frameH = Math.round(HEAD_H * s);

        // ---- the paper between the rods
        float bodyTop = yT + (HEAD_H - FADE) * s;
        float bodyBottom = fc - FOOT_ROD * s + fd;
        float openA = smooth(0.03f, 0.2f, e) * appear;
        int bodyH = Math.round(bodyBottom - bodyTop);
        int bodyTopI = Math.round(bodyTop);
        if (bodyH > 2 && openA > 0.01f) {
            Tex.Img body = p[1];
            float k = body.h / BODY_H;                      // texture pixels per picture pixel
            int tint = Draw.rgba(0xFFFFFFFF, openA);
            float speed = Math.abs(openVel);
            float amp = Math.min(4.5f * s, speed * 2.6f * s);
            if (amp < 0.25f) {
                int sh = Math.max(1, Math.round(bodyH / s * k));
                int sy = Math.max(0, Math.min(body.h - sh, Math.round(scroll * k)));
                body.drawPart(g, px, bodyTopI, w, bodyH, 0, sy, body.w, sh, tint);
            } else {
                // the paper ripples while it moves: slices of the picture, each a little to one side
                int slice = Math.max(4, Math.round(7f * s));
                for (int ry = 0; ry < bodyH; ry += slice) {
                    int rh = Math.min(slice, bodyH - ry);
                    int s0 = Math.round(scroll * k + ry / s * k);
                    int s1 = Math.round(scroll * k + (ry + rh) / s * k);
                    s0 = Math.max(0, Math.min(body.h - 1, s0));
                    s1 = Math.max(s0 + 1, Math.min(body.h, s1));
                    float near = Math.min(1f, Math.min(ry, bodyH - ry) / (24f * s));          // still at both rods
                    float dx = amp * near * (float) Math.sin(ry * 0.055f / Math.max(0.4f, s) - t * 16f);
                    body.drawPart(g, px + Math.round(dx), bodyTopI + ry, w, rh, 0, s0, body.w, s1 - s0, tint);
                }
            }
        }

        // ---- the bottom rod, coming out of the roll (the roll is drawn over it)
        float footA = smooth(0.0f, 0.03f, e + (open > 1f ? 1f : 0f)) * appear;
        if (footA > 0.01f) {
            int tintF = Draw.rgba(0xFFFFFFFF, footA);
            p[2].draw(g, px, Math.round(fc - FOOT_ROD * s), w, Math.round(FOOT_H * s), tintF);
        }

        // ---- the closed roll: turns slowly on its own, and towards the pointer while it is over it
        float rollA = 1f - smooth(0f, 0.05f, e);
        if (rollA > 0.01f) {
            float want = 3f + 2.4f * (float) Math.sin(t * 0.8f);
            if (hot) want = 3f + 3f * Math.max(-1f, Math.min(1f, (mx - (px + w / 2f)) / (w / 2f)));
            yaw += (want - yaw) * Math.min(1f, dt * 4f);
            float f = Math.max(0f, Math.min(VIEWS - 1.001f, yaw));
            int a = (int) Math.floor(f);
            float frac = f - a;
            int base = hot ? 0xFFFFFFFF : 0xFFE4E8F2;
            int yc = Math.round(tc - CLOSED_CY * s);
            int hc = Math.round((HEAD_H + FOOT_H) * s);
            p[3 + a].draw(g, px, yc, w, hc, Draw.rgba(base, appear * rollA));
            if (frac > 0.02f) p[3 + a + 1].draw(g, px, yc, w, hc, Draw.rgba(base, appear * rollA * frac));
            if (hot && rolled) {
                // a soft light round the roll while the pointer is on it
                Lightning.bloom(g, px + w / 2f, tc, w * 0.62f, 0xFFB8CCFF, 0.10f * appear);
            }
        }

        // ---- the roll that is opening: the frame for this much opening (two neighbours blended), as far down as the paper reaches
        float headA = smooth(0.84f, 1.0f, e) * appear;
        float tA = smooth(0f, 0.05f, e) * (1f - (headA >= 0.99f * appear ? 1f : 0f)) * appear;
        if (tA > 0.01f) {
            float fpos = e * (FRAMES - 1);
            int f0 = Math.min(FRAMES - 1, (int) Math.floor(fpos));
            int f1 = Math.min(FRAMES - 1, f0 + 1);
            float fr = fpos - f0;
            float k = unroll.h / (HEAD_H * (float) FRAMES);           // texture pixels per picture pixel
            float reach = Math.max(frameCy + rollR + 4f, Math.min(HEAD_H, (fc + 6f * s - yT) / s));
            int srcH = Math.max(1, Math.round(reach * k));
            int dstH = Math.max(1, Math.round(reach * s));
            int dy = Math.round(yT);
            int tint = Draw.rgba(0xFFFFFFFF, tA);
            unroll.drawPart(g, px, dy, w, dstH, 0, Math.round(f0 * HEAD_H * k), unroll.w, srcH, tint);
            if (fr > 0.03f && f1 != f0) {
                unroll.drawPart(g, px, dy, w, dstH, 0, Math.round(f1 * HEAD_H * k), unroll.w, srcH, Draw.rgba(0xFFFFFFFF, tA * fr));
            }
        }

        // ---- the top rod with the title: written onto the paper at the end
        if (headA > 0.01f) {
            int ty = Math.round(tc - HEAD_ROD * s);
            int tintH = hot ? Draw.rgba(0xFFFFFFFF, headA) : Draw.rgba(0xFFEDEDF2, headA);
            p[0].draw(g, px, ty, w, frameH, tintH);

            // a small arrow on the title band: up while the scroll is open
            int ax = px + w - Math.round(70 * s);
            int ay = ty + Math.round(79 * s);
            int arrow = Draw.rgba(hot ? 0xFF2A1C12 : 0xFF5C4430, headA);
            int half = Math.max(2, Math.round(7 * s));
            for (int i = 0; i < half; i++) {
                int len = i + 1;
                g.fill(ax - len, ay + i, ax + len + 1, ay + i + 1, arrow);
            }

            // the scroll bar: a thin line at the right edge of the parchment
            float max = maxScroll();
            if (e > 0.6f && max > 1f && bodyH > 2) {
                int tx = px + w - Math.round(52 * s) - Math.max(2, Math.round(3 * s));
                int tw = Math.max(2, Math.round(3.2f * s));
                int top = bodyTopI + Math.round(24 * s);
                int bottom = bodyTopI + bodyH - Math.round(24 * s);
                int track = Math.max(10, bottom - top);
                int barH = Math.max(10, Math.round(track * (bodyH / s) / BODY_H));
                int by = top + Math.round((track - barH) * (scroll / max));
                float oa = Math.min(1f, (e - 0.6f) / 0.4f) * appear;
                g.fill(tx, top, tx + tw, bottom, Draw.rgba(0xFF5A4632, 0.20f * oa));
                g.fillGradient(tx, by, tx + tw, by + barH, Draw.rgba(0xFF7A5A3C, 0.95f * oa), Draw.rgba(0xFF4A3424, 0.95f * oa));
            }
        }

        // ---- a label while the pointer is on the closed roll
        if (hot && rolled && open < 0.05f) {
            String tip = "News of Zeus";
            int tipW = Draw.width(tip) + 10;
            int tipX = Math.max(2, Math.min(screenW - tipW - 2, px + w / 2 - tipW / 2));
            int tipY = Math.max(2, Math.round(tc - CLOSED_CY * s) - 16);
            Draw.rrect(g, tipX, tipY, tipW, 13, 4, 0xF0050914);
            Draw.rrectLine(g, tipX, tipY, tipW, 13, 4, 0xFF8A7A62);
            Draw.text(g, tip, tipX + 5, tipY + 3, 0xFFE6DCC8);
        }
    }
}
