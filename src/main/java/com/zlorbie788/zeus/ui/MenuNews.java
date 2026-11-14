package com.zlorbie788.zeus.ui;

import net.minecraft.client.gui.GuiGraphics;

final class MenuNews {
    private static final float SRC_W = 580f;
    private static final float HEAD_H = 104f;
    private static final float FOOT_H = 80f;
    private static final float BODY_H = 691f;

    private static final float FADE = 16f;

    private static final float HEAD_CLICK = 96f;

    private static final int VIEWS = 7;

    private static final int FRAMES = 16;

    private static final float HEAD_ROD = 24f;
    private static final float FOOT_ROD = 52f;

    private static final float ROLL_R0 = 31f;
    private static final float ROLL_R1 = 13f;
    private static final float ROLL_CY0 = 36f;

    private static final float CLOSED_CY = 96f;

    private int x;

    private int w;

    private float topC;
    private float footC;
    private float bottomLine;

    private float openVel;

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
        float y0 = bottomLine - (FOOT_H + HEAD_H - CLOSED_CY) * s;
        float dFull = (HEAD_H + FOOT_H - HEAD_ROD - FOOT_ROD + (FOOT_ROD - 28f)) * s + bodyViewMax;
        float y1 = bottomLine - 28f * s - dFull;
        topC = y0 + (y1 - y0) * e;
        footC = topC + Math.max(0f, open) * dFull;
    }

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
        float bob = (1f - smooth(0f, 0.15f, e)) * 1.4f * s * (float) Math.sin(t * 1.3f);
        float tc = topC + bob;
        float fc = footC + bob;

        int fd = Math.round(FADE * s);
        float rollR = (float) Math.sqrt(ROLL_R0 * ROLL_R0 + (ROLL_R1 * ROLL_R1 - ROLL_R0 * ROLL_R0) * e);
        float frameCy = ROLL_CY0 + (HEAD_ROD - ROLL_CY0) * e;
        float yT = tc - frameCy * s;
        int frameH = Math.round(HEAD_H * s);

        float bodyTop = yT + (HEAD_H - FADE) * s;
        float bodyBottom = fc - FOOT_ROD * s + fd;
        float openA = smooth(0.03f, 0.2f, e) * appear;
        int bodyH = Math.round(bodyBottom - bodyTop);
        int bodyTopI = Math.round(bodyTop);
        if (bodyH > 2 && openA > 0.01f) {
            Tex.Img body = p[1];
            float k = body.h / BODY_H;
            int tint = Draw.rgba(0xFFFFFFFF, openA);
            float speed = Math.abs(openVel);
            float amp = Math.min(4.5f * s, speed * 2.6f * s);
            if (amp < 0.25f) {
                int sh = Math.max(1, Math.round(bodyH / s * k));
                int sy = Math.max(0, Math.min(body.h - sh, Math.round(scroll * k)));
                body.drawPart(g, px, bodyTopI, w, bodyH, 0, sy, body.w, sh, tint);
            } else {
                int slice = Math.max(4, Math.round(7f * s));
                for (int ry = 0; ry < bodyH; ry += slice) {
                    int rh = Math.min(slice, bodyH - ry);
                    int s0 = Math.round(scroll * k + ry / s * k);
                    int s1 = Math.round(scroll * k + (ry + rh) / s * k);
                    s0 = Math.max(0, Math.min(body.h - 1, s0));
                    s1 = Math.max(s0 + 1, Math.min(body.h, s1));
                    float near = Math.min(1f, Math.min(ry, bodyH - ry) / (24f * s));
                    float dx = amp * near * (float) Math.sin(ry * 0.055f / Math.max(0.4f, s) - t * 16f);
                    body.drawPart(g, px + Math.round(dx), bodyTopI + ry, w, rh, 0, s0, body.w, s1 - s0, tint);
                }
            }
        }

        if (bodyH > 8 && openA > 0.05f) drawNews(g, px, bodyTopI, w, bodyH, openA, t);

        float footA = smooth(0.0f, 0.03f, e + (open > 1f ? 1f : 0f)) * appear;
        if (footA > 0.01f) {
            int tintF = Draw.rgba(0xFFFFFFFF, footA);
            p[2].draw(g, px, Math.round(fc - FOOT_ROD * s), w, Math.round(FOOT_H * s), tintF);
        }

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
                Lightning.bloom(g, px + w / 2f, tc, w * 0.62f, 0xFFB8CCFF, 0.10f * appear);
            }
        }

        float headA = smooth(0.84f, 1.0f, e) * appear;
        float tA = smooth(0f, 0.05f, e) * (1f - (headA >= 0.99f * appear ? 1f : 0f)) * appear;
        if (tA > 0.01f) {
            float fpos = e * (FRAMES - 1);
            int f0 = Math.min(FRAMES - 1, (int) Math.floor(fpos));
            int f1 = Math.min(FRAMES - 1, f0 + 1);
            float fr = fpos - f0;
            float k = unroll.h / (HEAD_H * (float) FRAMES);
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

        if (headA > 0.01f) {
            int ty = Math.round(tc - HEAD_ROD * s);
            int tintH = hot ? Draw.rgba(0xFFFFFFFF, headA) : Draw.rgba(0xFFEDEDF2, headA);
            p[0].draw(g, px, ty, w, frameH, tintH);

            int ax = px + w - Math.round(70 * s);
            int ay = ty + Math.round(79 * s);
            int arrow = Draw.rgba(hot ? 0xFF2A1C12 : 0xFF5C4430, headA);
            int half = Math.max(2, Math.round(7 * s));
            for (int i = 0; i < half; i++) {
                int len = i + 1;
                g.fill(ax - len, ay + i, ax + len + 1, ay + i + 1, arrow);
            }

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

    // ------------------------------------------------------------------ the news itself
    private static final float TEXT_X0 = 96f;
    private static final float TEXT_X1 = 484f;
    private static final float TEXT_Y0 = 46f;

    /** Writes the news onto the parchment in dark ink, scrolling with the paper. */
    private void drawNews(GuiGraphics g, int px, int top, int w, int h, float alpha, float t) {
        java.util.List<MenuOnline.Line> lines = MenuOnline.news();
        float sc = Math.max(0.55f, Math.min(1.0f, s * 2.1f));
        int ink = Draw.rgba(0xFF3A2614, alpha);
        int soft = Draw.rgba(0xFF6A5236, alpha);
        int head = Draw.rgba(0xFF2A1A0C, alpha);
        int left = px + Math.round(TEXT_X0 * s);
        int maxW = Math.round((TEXT_X1 - TEXT_X0) * s);
        float y = top + (TEXT_Y0 - scroll) * s;
        g.enableScissor(px, top, px + w, top + h);
        int lineH = Math.max(6, Math.round(10 * sc));
        if (lines.isEmpty()) {
            Draw.boldCenterS(g, "Zeus News", px + w / 2, Math.round(y + 40 * s), head, sc * 1.25f);
            Draw.textCenterS(g, "connecting...", px + w / 2, Math.round(y + 40 * s + lineH * 1.6f), soft, sc);
            g.disableScissor();
            return;
        }
        for (MenuOnline.Line l : lines) {
            if (y > top + h) break;
            switch (l.kind()) {
                case 0 -> {
                    float hs = sc * 1.2f;
                    for (String part : wrap(l.text(), maxW, hs, true)) {
                        if (y + lineH > top) Draw.boldCenterS(g, part, px + w / 2, Math.round(y), head, hs);
                        y += lineH * 1.2f;
                    }
                    int lw = Math.round(maxW * 0.35f);
                    g.fill(px + w / 2 - lw / 2, Math.round(y), px + w / 2 + lw / 2, Math.round(y) + 1, soft);
                    y += lineH * 0.6f;
                }
                case 2 -> {
                    boolean first = true;
                    for (String part : wrap(l.text(), maxW - Math.round(8 * sc), sc, false)) {
                        if (first) Draw.textS(g, "\u2022", left, Math.round(y), ink, sc);
                        Draw.textS(g, part, left + Math.round(8 * sc), Math.round(y), ink, sc);
                        y += lineH;
                        first = false;
                    }
                }
                case 3 -> {
                    for (String part : wrap(l.text(), maxW, sc * 0.85f, false)) {
                        Draw.textCenterS(g, part, px + w / 2, Math.round(y), soft, sc * 0.85f);
                        y += lineH * 0.9f;
                    }
                }
                case 4 -> y += lineH * 0.6f;
                default -> {
                    for (String part : wrap(l.text(), maxW, sc, false)) {
                        Draw.textS(g, part, left, Math.round(y), ink, sc);
                        y += lineH;
                    }
                }
            }
        }
        g.disableScissor();
    }

    private static java.util.List<String> wrap(String text, int width, float sc, boolean bold) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            int wd = bold ? Draw.widthBoldS(next, sc) : Draw.widthS(next, sc);
            if (wd > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
