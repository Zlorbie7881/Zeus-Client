package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Crates;
import com.zlorbie788.zeus.core.Crates.Crate;
import com.zlorbie788.zeus.core.Crates.Item;
import com.zlorbie788.zeus.core.Crates.Rarity;
import com.zlorbie788.zeus.mods.KnifeSwing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Zeus Crates: pick a crate, see what is inside and the odds, open it with a key on a spinning reel,
 * and watch the knife come out. Also the collection of every knife and the promo code box.
 *
 * Everything that moves is driven by real time and eased, and every knife that is not the focus is
 * drawn from a cached still (see {@link KnifeHDGui#still}), so the screen stays smooth.
 */
public final class CratesScreen extends Screen {
    private static final String[] TABS = {"CRATES", "COLLECTION", "HISTORY", "REDEEM", "DAILY"};
    private static final int T_CRATES = 0, T_COLL = 1, T_HIST = 2, T_REDEEM = 3, T_DAILY = 4;
    private static final int GOLD = 0xFFF5C04A;
    private static final int TEXT = 0xFFE9F1FF;
    private static final int SUB = 0xFF8EA2CC;
    private static final int FAINT = 0xFF5E73A0;
    private static final int PANEL = 0xFF080C18;

    private final Screen parent;
    private final long born = System.nanoTime();
    private int tab;
    private int prevTab;
    private float tabAt = -10f;
    private int sel;
    private float selAt = -10f;
    private float openedAt;
    private float lastT;
    private int px, py, pw, ph;
    private float collScroll, collScrollShown;
    private EditBox codeBox;
    private String codeMsg = "";
    private boolean codeOk;
    private long codeAt;
    private String flash = "";
    private long flashAt;
    private Opening opening;

    // smoothed UI state
    private float underX = -1, underW;
    private float selBarY = -1;
    private final float[] rowHover = new float[8];
    private final Map<Integer, Float> hoverAnim = new HashMap<>();
    private int lastCrateCx, lastCrateCy, lastCrateSize = 110;
    private float shownTokens = -1, shownKeys = -1;
    private int lastTokens, lastKeys;
    private float histScroll, histScrollShown;
    private Item inspect;
    private float inspectAt;
    private float inspectYaw = -0.35f, inspectSpin;
    private boolean dragging;
    private double dragLastX;
    private final List<Object[]> floats = new ArrayList<>();

    // hit boxes rebuilt every frame
    private final List<int[]> hits = new ArrayList<>();
    private static final int H_TAB = 1, H_CRATE = 2, H_OPEN = 3, H_BUY = 4, H_REDEEM = 5, H_EQUIP = 6, H_AGAIN = 7, H_DONE = 8,
            H_COLL = 9, H_CLOSE = 10, H_FIELD = 11, H_QUICK = 12, H_INSPECT = 13, H_HIST = 14;

    public CratesScreen(Screen parent) {
        super(Component.literal("Zeus Crates"));
        this.parent = parent;
        Crates.load();
        for (int i = 0; i < Crates.CRATES.size(); i++) {
            if (Crates.held(Crates.CRATES.get(i).id) > 0) {
                sel = i;
                break;
            }
        }
    }

    private float now() {
        return (System.nanoTime() - born) / 1e9f;
    }

    // ------------------------------------------------------------------ easing
    private static float clamp01(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    private static float outCubic(float x) {
        x = clamp01(x);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float inOutCubic(float x) {
        x = clamp01(x);
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float outBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    /** Frame-rate independent approach of a value towards a target. */
    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * (1f - (float) Math.exp(-rate * dt));
    }

    private float hover(int key, boolean on, float dt) {
        float v = hoverAnim.getOrDefault(key, 0f);
        v = approach(v, on ? 1f : 0f, 16f, dt);
        hoverAnim.put(key, v);
        return v;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        Crates.save(true);
        minecraft.setScreen(parent);
    }

    private void layout() {
        pw = Math.min(width - 16, 720);
        ph = Math.min(height - 16, 400);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    protected void init() {
        layout();
        String keep = codeBox == null ? "" : codeBox.getValue();
        codeBox = new EditBox(font, 0, 0, 220, 18, Component.literal("Promo code"));
        codeBox.setMaxLength(24);
        codeBox.setBordered(false);
        codeBox.setTextColor(0xFFFFFFFF);
        codeBox.setHint(Component.literal("ZEUS-XXXX-XXXX"));
        codeBox.setValue(keep);
        addRenderableWidget(codeBox);
        placeCodeBox();
    }

    private void placeCodeBox() {
        int bw = 220;
        codeBox.setX(px + pw / 2 - bw / 2 + 8);
        codeBox.setY(py + 97);
        codeBox.setWidth(bw - 16);
        codeBox.visible = tab == T_REDEEM && opening == null && inspect == null;
        codeBox.active = codeBox.visible;
    }

    // ------------------------------------------------------------------ sound
    private static void snd(Object e, float pitch, float vol) {
        try {
            var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
            if (e instanceof Holder<?> h) {
                @SuppressWarnings("unchecked") Holder<SoundEvent> hs = (Holder<SoundEvent>) h;
                sm.play(SimpleSoundInstance.forUI(hs.value(), pitch, vol));
            } else if (e instanceof SoundEvent s) {
                sm.play(SimpleSoundInstance.forUI(s, pitch, vol));
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ drawing helpers
    private void hit(int kind, int idx, int x, int y, int w, int h) {
        hits.add(new int[] {kind, idx, x, y, w, h});
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int kind, int x, int y, int w, int h, String label, int colour, boolean enabled, boolean primary,
                        int mx, int my, float t, float dt) {
        boolean hot = enabled && in(mx, my, x, y, w, h);
        float hv = hover(1000 + kind, hot, dt);
        g.pose().pushMatrix();
        float sc = 1f + 0.035f * hv;
        g.pose().translate(x + w / 2f, y + h / 2f);
        g.pose().scale(sc, sc);
        g.pose().translate(-(x + w / 2f), -(y + h / 2f));
        if (primary && enabled) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.2f);
            Draw.glow(g, x, y, w, h, 6, colour, 9, 0.14f + 0.10f * pulse + 0.18f * hv);
            Draw.rrect(g, x, y, w, h, 6, Draw.lighten(colour, 0.04f + 0.16f * hv), Draw.darken(colour, 0.25f));
            Draw.sheen(g, x, y, w, h, 0x60FFFFFF, (t * 0.45f) % 1.6f);
            Draw.boldCenter(g, label, x + w / 2, y + (h - 8) / 2, 0xFF1A1206);
        } else {
            int bg = !enabled ? 0xFF10141E : Draw.mix(0xFF141C30, 0xFF1E2A48, hv);
            Draw.rrect(g, x, y, w, h, 6, bg);
            Draw.rrectLine(g, x, y, w, h, 6, !enabled ? 0xFF1E2638 : Draw.mix(Draw.darken(colour, 0.35f), Draw.lighten(colour, 0.2f), hv));
            Draw.boldCenter(g, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : 0xFF4A5670);
        }
        g.pose().popMatrix();
        if (enabled) hit(kind, 0, x, y, w, h);
    }

    /**
     * A Zeus crate drawn from boxes: body, straps, a glowing emblem and a lid. The lid can lift and
     * turn away (opening), and the whole crate can shake.
     */
    private void drawCrate(GuiGraphics g, Crate c, float cx, float cy, int size, float t, float lid, float shake, float glowBoost) {
        int w = size, h = Math.round(size * 0.72f);
        float sx = shake * (float) Math.sin(t * 61) + shake * 0.5f * (float) Math.sin(t * 97);
        float sy = shake * 0.6f * (float) Math.cos(t * 53);
        float rot = shake * 0.006f * (float) Math.sin(t * 43);
        g.pose().pushMatrix();
        g.pose().translate(cx + sx, cy + sy);
        g.pose().rotate(rot);
        int x = -w / 2, y = -h / 2;
        int r = Math.max(2, size / 14);
        Draw.shadow(g, x, y + h / 6, w, h * 5 / 6, r, Math.max(3, size / 10), 0.55f);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 2.0f);
        Draw.glow(g, x, y, w, h, r, c.colour, Math.max(4, size / 6), 0.05f + 0.05f * breath + glowBoost);
        int bodyY = y + h / 5;
        Draw.rrect(g, x, bodyY, w, h - h / 5, r, Draw.mix(c.dark, 0xFF1A2030, 0.35f), Draw.darken(c.dark, 0.45f));
        Draw.rrectLine(g, x, bodyY, w, h - h / 5, r, Draw.darken(c.colour, 0.1f));
        int sw = Math.max(3, w / 9);
        for (int s = 0; s < 2; s++) {
            int stx = s == 0 ? x + w / 5 - sw / 2 : x + w * 4 / 5 - sw / 2;
            g.fill(stx, bodyY + 1, stx + sw, y + h - 1, Draw.darken(c.colour, 0.55f));
            g.fill(stx, bodyY + 1, stx + 1, y + h - 1, Draw.rgba(c.colour, 0.6f));
            g.fill(stx + sw - 1, bodyY + 1, stx + sw, y + h - 1, Draw.rgba(c.colour, 0.6f));
        }
        int er = Math.max(4, size / 7);
        int ecy = bodyY + (h - h / 5) / 2 + 2;
        float pulse = Math.min(1f, breath + glowBoost * 3f);
        Draw.glow(g, -er, ecy - er, er * 2, er * 2, er, c.colour, Math.max(3, er), 0.10f + 0.12f * pulse);
        Draw.rrect(g, -er, ecy - er, er * 2, er * 2, er, Draw.darken(c.colour, 0.55f));
        Draw.rrectLine(g, -er, ecy - er, er * 2, er * 2, er, Draw.lighten(c.colour, 0.2f * pulse));
        float sc = Math.max(0.6f, er / 6f);
        Draw.boldCenterS(g, "Z", 0, ecy - Math.round(4 * sc), Draw.lighten(c.colour, 0.3f + 0.3f * Math.min(1f, glowBoost)), sc);
        // light leaking from the seam as the lid is about to go
        int lh = Math.max(4, h / 4);
        if (glowBoost > 0.02f || lid > 0f) {
            float k = Math.min(1f, glowBoost * 2f + lid * 3f);
            Draw.glow(g, x + 2, bodyY - 1, w - 4, 3, 1, Draw.lighten(c.colour, 0.5f), Math.max(4, size / 5), 0.30f * k);
            g.fill(x + 3, bodyY - 1, x + w - 3, bodyY + 1, Draw.rgba(0xFFFFF6D8, k));
        }
        float la = 1f - clamp01((lid - 0.55f) / 0.45f);
        if (la > 0f) {
            g.pose().pushMatrix();
            float lift = lid * size * 0.9f;
            g.pose().translate(lid * size * 0.25f, y - lift);
            g.pose().rotate(-lid * 0.9f);
            int lw = w + 2 * Math.max(2, size / 30);
            Draw.rrect(g, -lw / 2, 0, lw, lh, r, Draw.rgba(Draw.mix(c.dark, 0xFF2A3044, 0.5f), la), Draw.rgba(Draw.darken(c.dark, 0.2f), la));
            Draw.rrectLine(g, -lw / 2, 0, lw, lh, r, Draw.rgba(c.colour, la));
            g.fill(-Math.max(3, w / 12), lh - 2, Math.max(3, w / 12), lh + Math.max(3, h / 9), Draw.rgba(c.colour, la));
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }

    private static String shortName(String full) {
        int dot = full.lastIndexOf('·');
        return dot >= 0 ? full.substring(dot + 1).trim() : full;
    }

    private static String typeName(String full) {
        int dot = full.indexOf('·');
        return dot >= 0 ? full.substring(0, dot).trim() : full;
    }

    private static final Map<String, String> NAMES = new HashMap<>();

    private static String nameOf(String id) {
        String n = NAMES.get(id);
        if (n != null) return n;
        int hd = Crates.hdOf(id);
        KnifeHD.Model m = hd >= 0 ? KnifeHD.model(hd) : null;
        n = m == null ? id : m.name;
        NAMES.put(id, n);
        return n;
    }

    private static int kindOf(String id) {
        int hd = Crates.hdOf(id);
        return hd < 0 ? -1 : KnifeArt.allKnives().length - KnifeArt.HD_COUNT + hd;
    }

    /** One knife card: dark glass tinted with the rarity, the knife, its name, a rarity strip. */
    private void card(GuiGraphics g, Item it, int x, int y, int w, int h, boolean owned, boolean locked, float live, float t) {
        int rc = it.rarity().colour;
        Draw.rrect(g, x, y, w, h, 5, Draw.mix(0xFF0C1020, rc, 0.20f + 0.08f * live), 0xFF080B16);
        Draw.rrectLine(g, x, y, w, h, 5, Draw.mix(Draw.darken(rc, 0.35f), Draw.lighten(rc, 0.2f), live));
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 2, rc);
        boolean named = h >= 44;
        int kind = kindOf(it.id());
        int kh = h - (named ? 20 : 8);
        if (kind >= 0) {
            if (live > 0.5f) KnifeArt.showcase(g, kind, 0, x + 3, y + 3, w - 6, kh, t, false);
            else KnifeArt.still(g, kind, x + 3, y + 3, w - 6, kh);
        }
        if (locked) {
            Draw.rrect(g, x, y, w, h, 5, 0xB0060810);
            Draw.boldCenter(g, "?", x + w / 2, y + h / 2 - 10, 0x80A0B0D0);
        }
        if (named) Draw.textCenter(g, Draw.fit(shortName(nameOf(it.id())), w - 6), x + w / 2, y + h - 15, locked ? 0xFF5E6A88 : TEXT);
        if (owned) {
            Draw.rrect(g, x + w - 13, y + 3, 10, 10, 5, 0xFF1E8A4A);
            Draw.textCenter(g, "✔", x + w - 8, y + 4, 0xFFFFFFFF);
        }
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        layout();
        placeCodeBox();
        hits.clear();
        float t = now();
        float dt = Math.max(0f, Math.min(0.1f, t - lastT));
        lastT = t;
        float intro = outCubic((t - openedAt) / 0.32f);
        g.fill(0, 0, width, height, Draw.rgba(0xC0030610, intro));
        int sc = Crates.CRATES.get(sel).colour;
        Draw.glow(g, px + pw / 4, py + ph / 3, pw / 2, ph / 3, 40, sc, 60, 0.004f * intro);

        // the panel scales up a touch as it appears
        g.pose().pushMatrix();
        float ps = 0.94f + 0.06f * outBack((t - openedAt) / 0.38f);
        g.pose().translate(width / 2f, height / 2f);
        g.pose().scale(ps, ps);
        g.pose().translate(-width / 2f, -height / 2f);
        Draw.shadow(g, px, py, pw, ph, 10, 12, 0.6f);
        Draw.rrect(g, px, py, pw, ph, 10, 0xF4080C18, 0xF4050812);
        Draw.rrectLine(g, px, py, pw, ph, 10, 0xFF2A3E6A);
        header(g, mx, my, t, dt);

        // the tab content slides in from the side it was picked from
        float tp = outCubic((t - tabAt) / 0.30f);
        float dir = tab >= prevTab ? 1f : -1f;
        g.pose().pushMatrix();
        g.pose().translate((1f - tp) * 18f * dir, 0f);
        if (tab == 0) cratesTab(g, mx, my, t, dt);
        else if (tab == T_COLL) collectionTab(g, mx, my, t, dt);
        else if (tab == T_HIST) historyTab(g, mx, my, t, dt);
        else if (tab == T_DAILY) dailyTab(g, mx, my, t, dt);
        else redeemTab(g, mx, my, t, dt);
        g.pose().popMatrix();
        if (tp < 1f) g.fill(px + 2, py + 56, px + pw - 2, py + ph - 32, Draw.rgba(PANEL, (1f - tp) * 0.85f));
        footer(g, mx, my, t);
        g.pose().popMatrix();

        if (opening == null && inspect == null && tp >= 1f) super.render(g, mx, my, partial);
        if (opening == null && inspect != null) renderInspect(g, mx, my, t, dt);
        if (opening != null) opening.render(g, mx, my, t, dt);
        floaters(g, t);

        if (!flash.isEmpty()) {
            float a = 1f - Math.max(0f, (System.currentTimeMillis() - flashAt - 2200) / 600f);
            if (a <= 0) flash = "";
            else Draw.toast(g, minecraft, width / 2, py + ph - 52, flash, true, a);
        }
    }

    private void header(GuiGraphics g, int mx, int my, float t, float dt) {
        Draw.boldS(g, "ZEUS", px + 14, py + 12, GOLD, 1.25f);
        Draw.boldS(g, "CRATES", px + 14 + Draw.widthBoldS("ZEUS ", 1.25f), py + 12, TEXT, 1.25f);
        int realT = Crates.tokens(), realK = Crates.keys();
        if (shownTokens < 0) {
            shownTokens = realT;
            shownKeys = realK;
            lastTokens = realT;
            lastKeys = realK;
        }
        if (realT != lastTokens) {
            floater((realT > lastTokens ? "+" : "") + (realT - lastTokens), realT > lastTokens ? GOLD : 0xFFFF8A7A, px + pw - 14 - 64 - 40, py + 30, t);
            lastTokens = realT;
        }
        if (realK != lastKeys) {
            floater((realK > lastKeys ? "+" : "") + (realK - lastKeys) + " key", realK > lastKeys ? 0xFFC8D4F0 : 0xFFFF8A7A, px + pw - 14 - 32, py + 30, t);
            lastKeys = realK;
        }
        shownTokens = approach(shownTokens, realT, 9f, dt);
        shownKeys = approach(shownKeys, realK, 9f, dt);
        String tk = Math.round(shownTokens) + "";
        int cw = 34 + Draw.widthBold(tk) + 40;
        int kx = px + pw - 14 - 64;
        int tx = kx - 8 - cw;
        Draw.rrect(g, tx, py + 9, cw, 18, 9, 0xFF141A2C);
        Draw.rrectLine(g, tx, py + 9, cw, 18, 9, 0xFF3A3420);
        Draw.rrect(g, tx + 4, py + 12, 12, 12, 6, GOLD, 0xFFB07A18);
        Draw.boldCenter(g, "Z", tx + 10, py + 14, 0xFF3A2A08);
        Draw.bold(g, tk, tx + 21, py + 14, GOLD);
        Draw.text(g, "TOKENS", tx + 24 + Draw.widthBold(tk), py + 14, SUB);
        Draw.rrect(g, kx, py + 9, 64, 18, 9, 0xFF141A2C);
        Draw.rrectLine(g, kx, py + 9, 64, 18, 9, 0xFF2A3E6A);
        drawKey(g, kx + 6, py + 13, 0xFFC8D4F0);
        String ks = Math.round(shownKeys) + "";
        Draw.bold(g, ks, kx + 24, py + 14, TEXT);
        Draw.text(g, "KEYS", kx + 30 + Draw.widthBold(ks), py + 14, SUB);

        // tabs, with an underline that glides to the chosen one
        int x = px + 14;
        float wantX = 0, wantW = 0;
        for (int i = 0; i < TABS.length; i++) {
            int w = Draw.widthBold(TABS[i]) + 18;
            boolean on = i == tab, hot = in(mx, my, x, py + 34, w, 16);
            float hv = hover(2000 + i, hot || on, dt);
            if (on) {
                wantX = x + 4;
                wantW = w - 8;
            }
            Draw.boldCenter(g, TABS[i], x + w / 2, py + 38, Draw.mix(FAINT, TEXT, hv));
            hit(H_TAB, i, x, py + 34, w, 16);
            x += w + 6;
        }
        int tabsEnd = x;
        // quick open switch, left of the close button
        boolean q = Crates.quick();
        String ql = "QUICK OPEN";
        if (px + pw - 36 - (Draw.width(ql) + 30) < tabsEnd + 6) ql = "QUICK";
        if (px + pw - 36 - (Draw.width(ql) + 30) < tabsEnd + 6) ql = "";
        int qw = ql.isEmpty() ? 22 : Draw.width(ql) + 30, qx = px + pw - 36 - qw, qy = py + 35;
        boolean qhot = in(mx, my, qx, qy, qw, 13);
        float qv = hover(6000, q, dt);
        Draw.rrect(g, qx, qy + 2, 20, 10, 5, Draw.mix(0xFF1A2236, GOLD, qv));
        Draw.rrect(g, qx + 1 + Math.round(qv * 10f), qy + 3, 8, 8, 4, Draw.mix(0xFF8EA2CC, 0xFF1A1206, qv));
        if (!ql.isEmpty()) Draw.text(g, ql, qx + 25, qy + 3, qhot ? TEXT : q ? 0xFFF5D08A : FAINT);
        if (qhot) {
            String tip = q ? "Quick open is on: shorter animation" : "Quick open: shorter opening animation";
            int tw = Draw.width(tip) + 10;
            Draw.rrect(g, Math.min(qx, px + pw - tw - 8), qy + 15, tw, 12, 4, 0xF00A0E1C);
            Draw.text(g, tip, Math.min(qx, px + pw - tw - 8) + 5, qy + 17, SUB);
        }
        hit(H_QUICK, 0, qx, qy, qw, 13);
        if (underX < 0) {
            underX = wantX;
            underW = wantW;
        }
        underX = approach(underX, wantX, 18f, dt);
        underW = approach(underW, wantW, 18f, dt);
        Draw.glow(g, Math.round(underX), py + 50, Math.round(underW), 2, 1, GOLD, 5, 0.2f);
        g.fill(Math.round(underX), py + 50, Math.round(underX + underW), py + 52, GOLD);
        g.fill(px + 12, py + 53, px + pw - 12, py + 54, 0xFF18223A);
        hit(H_CLOSE, 0, px + pw - 28, py + 34, 14, 14);
        Draw.boldCenter(g, "✕", px + pw - 21, py + 37, in(mx, my, px + pw - 28, py + 34, 14, 14) ? TEXT : FAINT);
    }

    private static void drawKey(GuiGraphics g, int x, int y, int c) {
        Draw.rrectLine(g, x, y, 8, 8, 4, c);
        g.fill(x + 7, y + 3, x + 15, y + 5, c);
        g.fill(x + 12, y + 5, x + 14, y + 8, c);
    }

    private void footer(GuiGraphics g, int mx, int my, float t) {
        int y = py + ph - 24;
        g.fill(px + 12, y - 6, px + pw - 12, y - 5, 0xFF18223A);
        long ms = Crates.nextTokenIn();
        String a = "Next token in " + clock(ms) + (Crates.activeNow() ? "" : "  (paused)");
        Draw.text(g, a, px + 14, y + 2, SUB);
        int bx = px + 14 + Draw.width(a) + 10, bw = 90;
        Draw.rrect(g, bx, y + 3, bw, 5, 2, 0xFF141C30);
        int fill = Math.max(2, Math.round(bw * Crates.tokenProgress()));
        Draw.rrect(g, bx, y + 3, fill, 5, 2, GOLD);
        if (Crates.activeNow()) Draw.sheen(g, bx, y + 3, fill, 5, 0x70FFFFFF, (t * 0.35f) % 1.6f);
        String d = "Free crate drop in " + clock(Crates.nextDropIn());
        Draw.textRight(g, d, px + pw - 14, y + 2, FAINT);
        // daily streak, in the middle when there is room
        String st;
        int sc;
        if (Crates.streakClaimedToday()) {
            st = "Day " + Crates.streak() + " streak claimed. Tomorrow: +" + Crates.streakReward(Crates.streakNextDay() + 1) + " tokens";
            sc = GOLD;
        } else {
            int n = Crates.streakNextDay();
            st = "Play " + clock(Crates.streakPlayLeft()) + " more today for streak day " + n + ": +" + Crates.streakReward(n) + " tokens";
            sc = SUB;
        }
        int left = bx + bw + 16, right = px + pw - 14 - Draw.width(d) - 16;
        if (Draw.width(st) <= right - left) Draw.textCenter(g, st, (left + right) / 2, y + 2, sc);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ------------------------------------------------------------------ crates tab
    private void cratesTab(GuiGraphics g, int mx, int my, float t, float dt) {
        int top = py + 62, bottom = py + ph - 34;
        boolean small = pw < 560;
        int lw = small ? 96 : Math.max(120, pw * 24 / 100);
        int rowH = Math.max(18, Math.min(40, (bottom - top) / Crates.CRATES.size()));
        // selection bar glides between rows
        float wantBar = top + sel * rowH;
        if (selBarY < 0) selBarY = wantBar;
        selBarY = approach(selBarY, wantBar, 16f, dt);
        Crate cs0 = Crates.CRATES.get(sel);
        int by0 = Math.round(selBarY);
        Draw.rrect(g, px + 12, by0, lw, rowH - 4, 6, Draw.mix(0xFF0C1222, cs0.colour, 0.18f));
        Draw.rrectLine(g, px + 12, by0, lw, rowH - 4, 6, cs0.colour);
        g.fill(px + 12, by0 + 4, px + 14, by0 + rowH - 8, cs0.colour);
        for (int i = 0; i < Crates.CRATES.size(); i++) {
            Crate c = Crates.CRATES.get(i);
            int x = px + 12, y = top + i * rowH, w = lw, h = rowH - 4;
            boolean on = i == sel, hot = in(mx, my, x, y, w, h);
            rowHover[i] = approach(rowHover[i], hot && !on ? 1f : 0f, 14f, dt);
            if (!on) Draw.rrect(g, x, y, w, h, 6, Draw.mix(0xFF0B1124, 0xFF111A30, rowHover[i]));
            int shift = Math.round(rowHover[i] * 3f);
            drawCrate(g, c, x + 18 + shift, y + h / 2f + 1, Math.min(26, h), t + i, 0f, 0f, 0f);
            int n = Crates.held(c.id);
            int badgeW = n > 0 ? Draw.widthBold("x" + n) + 14 : 0;
            int nameW = w - 40 - badgeW;
            int tc = on ? TEXT : Draw.mix(0xFFB8C6E4, TEXT, rowHover[i]);
            if (h >= 26 && !small) {
                Draw.bold(g, Draw.fit(c.name.replace(" Crate", ""), nameW), x + 36 + shift, y + h / 2 - 8, tc);
                int got = Crates.collected(c);
                Draw.text(g, Draw.fit(got + " / " + c.items.size() + " collected", nameW), x + 36 + shift, y + h / 2 + 2, got == c.items.size() ? GOLD : FAINT);
            } else {
                Draw.bold(g, Draw.fit(c.name.replace(" Crate", ""), nameW), x + 34 + shift, y + h / 2 - 4, tc);
            }
            if (n > 0) {
                String s = "x" + n;
                int bw = Draw.widthBold(s) + 8;
                Draw.rrect(g, x + w - bw - 6, y + h / 2 - 6, bw, 12, 6, c.colour);
                Draw.boldCenter(g, s, x + w - bw / 2 - 6, y + h / 2 - 4, 0xFF0A0A10);
            }
            hit(H_CRATE, i, x, y, w, h);
        }
        Crate c = Crates.CRATES.get(sel);
        // centre: the crate itself, popping in when picked
        int cx0 = px + 12 + lw + 12;
        int cw = small ? 128 : Math.max(150, pw * 30 / 100);
        int ccx = cx0 + cw / 2;
        int avail = bottom - top;
        boolean compact = avail < 175;
        int cs = Math.max(compact ? 36 : 44, Math.min(Math.min(cw - 30, 130), Math.round((avail - (compact ? 72 : 100)) / 1.07f)));
        float pop = outBack((t - selAt) / 0.45f);
        float bob = (float) Math.sin(t * 1.6f) * Math.max(1.5f, cs / 40f);
        int need = Math.round(6 + cs * 1.07f + (compact ? 64 : 100));
        int yC = top + Math.max(6, (avail - need) / 2 + 6) + Math.round(cs * 0.62f);
        Draw.glow(g, ccx - cw / 4, yC - cs / 4, cw / 2, cs / 2, 30, c.colour, 34, 0.010f + 0.012f * (1f - clamp01((t - selAt) / 0.6f)));
        int size = Math.max(8, Math.round(cs * (0.82f + 0.18f * pop)));
        drawCrate(g, c, ccx, yC + bob, size, t, 0f, 0f, 0.25f * (1f - clamp01((t - selAt) / 0.5f)));
        lastCrateCx = ccx;
        lastCrateCy = Math.round(yC + bob);
        lastCrateSize = cs;
        int ny = yC + Math.round(cs * 0.45f) + 4;
        float ta = outCubic((t - selAt - 0.08f) / 0.3f);
        int lift = Math.round((1f - ta) * 6f);
        Draw.boldCenterS(g, Draw.fit(c.name.toUpperCase(Locale.ROOT), cw), ccx, ny + lift, Draw.rgba(c.colour, ta), small ? 1f : 1.15f);
        if (!compact) Draw.textCenter(g, Draw.fit(c.tagline, cw), ccx, ny + 13 + lift, Draw.rgba(SUB, ta));
        else ny -= 13;
        int have = Crates.held(c.id);
        Draw.textCenter(g, Draw.fit(have > 0 ? "You have " + have + "  ·  " + Crates.keys() + " key" + (Crates.keys() == 1 ? "" : "s") : "You have none yet", cw), ccx, ny + 24, have > 0 ? TEXT : FAINT);
        boolean canOpen = Crates.canOpen(c);
        int bw = Math.min(cw - 6, 150);
        button(g, H_OPEN, ccx - bw / 2, ny + 37, bw, 20, canOpen ? "OPEN  \u00b7  " + Crates.KEYS_PER_OPEN + " KEYS" : have > 0 ? "NEEDS " + Crates.KEYS_PER_OPEN + " KEYS" : "NO CRATE", c.colour, canOpen, true, mx, my, t, dt);
        boolean canBuy = Crates.tokens() >= Crates.KEY_PRICE;
        button(g, H_BUY, ccx - bw / 2, ny + 61, bw, 16, small ? "KEY  ·  " + Crates.KEY_PRICE + " TOKENS" : "BUY KEY  ·  " + Crates.KEY_PRICE + " TOKENS", GOLD, canBuy, false, mx, my, t, dt);
        if (!compact) {
            int pi = Crates.pityIn();
            String pity = pi == 1 ? "Next open: Divine+ guaranteed" : "Divine+ guaranteed in " + pi + " opens";
            Draw.textCenter(g, Draw.fit(pity, cw), ccx, ny + 92, pi <= 3 ? 0xFFD32CE6 : FAINT);
            String hint = !canBuy ? (Crates.KEY_PRICE - Crates.tokens()) + " more tokens for a key"
                    : Crates.tokens() >= Crates.KEY_PRICE * 2 ? "Shift-click to buy " + Math.min(Crates.KEYS_PER_OPEN, Crates.tokens() / Crates.KEY_PRICE) + " at once" : "";
            if (!hint.isEmpty()) Draw.textCenter(g, Draw.fit(hint, cw), ccx, ny + 80, FAINT);
        }
        // right: contents drop in one after another
        int rx = cx0 + cw + 10, rw = px + pw - 12 - rx;
        Draw.bold(g, "INSIDE", rx, top, TEXT);
        int got = Crates.collected(c);
        String gs = got + " / " + c.items.size();
        Draw.textRight(g, gs, rx + rw, top, got == c.items.size() ? GOLD : SUB);
        int pbx = rx + Draw.widthBold("INSIDE") + 8, pbw = rw - Draw.widthBold("INSIDE") - 16 - Draw.width(gs);
        if (pbw > 20) {
            Draw.rrect(g, pbx, top + 3, pbw, 3, 1, 0xFF141C30);
            Draw.rrect(g, pbx, top + 3, Math.max(2, Math.round(pbw * got / (float) c.items.size())), 3, 1, c.colour);
        }
        int cols = 4, gap = 4;
        int tw = (rw - (cols - 1) * gap) / cols;
        int rows = (c.items.size() + cols - 1) / cols;
        int th = Math.max(26, Math.min(52, (bottom - top - 14 - 26 - (rows - 1) * gap) / rows));
        Item hoverIt = null;
        for (int i = 0; i < c.items.size(); i++) {
            Item it = c.items.get(i);
            int x = rx + (i % cols) * (tw + gap), y = top + 12 + (i / cols) * (th + gap);
            float e = outCubic((t - selAt - 0.04f - i * 0.028f) / 0.28f);
            if (e <= 0f) continue;
            boolean hot = in(mx, my, x, y, tw, th);
            float hv = hover(3000 + i, hot, dt);
            int yy = y + Math.round((1f - e) * 10f) - Math.round(hv * 2f);
            card(g, it, x, yy, tw, th, Crates.owns(it.id()), false, hv, t * 0.8f);
            if (e < 1f) Draw.rrect(g, x, yy, tw, th, 5, Draw.rgba(PANEL, 1f - e));
            if (hot) hoverIt = it;
            hit(H_INSPECT, i, x, y, tw, th);
        }
        int oy = top + 12 + rows * (th + gap) + 4;
        Rarity[] rs = Rarity.values();
        if (rw < 260) {
            int x = rx;
            for (int i = rs.length - 1; i >= 0; i--) {
                String o = rs[i].odds();
                Draw.rrect(g, x, oy + 1, 6, 6, 3, rs[i].colour);
                Draw.text(g, o, x + 8, oy, 0xFFB0BEDC);
                x += 8 + Draw.width(o) + 7;
            }
            Draw.text(g, Draw.fit("Olympian → Mortal", rw), rx, oy + 11, FAINT);
        } else {
            for (int i = rs.length - 1, k = 0; i >= 0; i--, k++) {
                if (oy + (k / 3) * 11 > bottom - 8) break;
                int x = rx + (k % 3) * (rw / 3), y = oy + (k / 3) * 11;
                Draw.rrect(g, x, y + 1, 6, 6, 3, rs[i].colour);
                Draw.text(g, rs[i].label + " " + rs[i].odds(), x + 9, y, 0xFFB0BEDC);
            }
        }
        if (hoverIt != null) tooltip(g, mx, my, hoverIt);
    }

    private void tooltip(GuiGraphics g, int mx, int my, Item it) {
        String n = nameOf(it.id());
        Crate from = Crates.crateOf(it.id());
        String l2 = it.rarity().label + (from == null ? "" : "  ·  " + from.name);
        String l3 = Crates.owns(it.id()) ? "Owned" : "Not owned yet";
        int w = Math.max(Draw.widthBold(n), Math.max(Draw.width(l2), Draw.width(l3))) + 16;
        int x = Math.min(mx + 10, width - w - 4), y = Math.min(my + 10, height - 44);
        Draw.rrect(g, x, y, w, 40, 5, 0xF00A0E1C);
        Draw.rrectLine(g, x, y, w, 40, 5, it.rarity().colour);
        Draw.bold(g, n, x + 8, y + 5, TEXT);
        Draw.text(g, l2, x + 8, y + 17, it.rarity().colour);
        Draw.text(g, l3, x + 8, y + 28, Crates.owns(it.id()) ? 0xFF6FE3A0 : FAINT);
    }

    // ------------------------------------------------------------------ collection tab
    private static List<Item> ALL;

    private static List<Item> allItems() {
        if (ALL == null) {
            List<Item> l = new ArrayList<>();
            l.add(new Item(Crates.STARTER, Rarity.MORTAL));
            for (Crate c : Crates.CRATES) l.addAll(c.items);
            ALL = l;
        }
        return ALL;
    }

    private void collectionTab(GuiGraphics g, int mx, int my, float t, float dt) {
        int top = py + 62, bottom = py + ph - 34;
        List<Item> all = allItems();
        Draw.bold(g, "YOUR KNIVES", px + 14, top, TEXT);
        Draw.text(g, Crates.ownedCount() + " / " + all.size() + " collected  ·  click one you own to hold it", px + 14 + Draw.widthBold("YOUR KNIVES") + 8, top, SUB);
        int cols = Math.max(4, (pw - 24) / 74);
        int gap = 5;
        int tw = (pw - 24 - (cols - 1) * gap) / cols, th = 60;
        int gy = top + 14, gh = bottom - gy;
        int rows = (all.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, rows * (th + gap) - gh);
        collScroll = Math.max(0, Math.min(maxScroll, collScroll));
        collScrollShown = approach(collScrollShown, collScroll, 18f, dt);
        g.enableScissor(px + 10, gy, px + pw - 10, gy + gh);
        Item hoverIt = null;
        int held = KnifeSwing.heldHd();
        for (int i = 0; i < all.size(); i++) {
            int x = px + 12 + (i % cols) * (tw + gap), y = gy + (i / cols) * (th + gap) - Math.round(collScrollShown);
            if (y + th < gy || y > gy + gh) continue;
            Item it = all.get(i);
            boolean own = Crates.owns(it.id());
            boolean hot = in(mx, my, x, y, tw, th) && my >= gy && my < gy + gh;
            float hv = own ? hover(4000 + i, hot, dt) : 0f;
            int yy = y - Math.round(hv * 2f);
            card(g, it, x, yy, tw, th, false, !own, hv, t * 0.8f);
            if (own && Crates.hdOf(it.id()) == held) {
                Draw.rrectLine(g, x - 1, yy - 1, tw + 2, th + 2, 6, 0xFF6FE3A0);
                Draw.text(g, "HELD", x + 4, yy + 4, 0xFF6FE3A0);
            }
            if (hot) {
                hoverIt = it;
                hit(H_COLL, i, x, y, tw, th);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int bh = Math.max(16, gh * gh / (rows * (th + gap)));
            int by = gy + Math.round((gh - bh) * collScrollShown / maxScroll);
            Draw.rrect(g, px + pw - 9, by, 3, bh, 1, 0xFF2A3E6A);
        }
        if (hoverIt != null) tooltip(g, mx, my, hoverIt);
    }

    // ------------------------------------------------------------------ redeem tab
    private void redeemTab(GuiGraphics g, int mx, int my, float t, float dt) {
        int cx = px + pw / 2;
        int top = py + 62;
        Draw.boldCenterS(g, "REDEEM A CODE", cx, top, TEXT, 1.4f);
        Draw.textCenter(g, "Promo codes unlock free crates, each with its own key.", cx, top + 18, SUB);
        int bw = 220;
        int bx = cx - bw / 2, by = py + 90;
        boolean focus = codeBox.isFocused();
        float fv = hover(5000, focus, dt);
        Draw.glow(g, bx, by, bw, 22, 6, GOLD, 8, 0.06f + 0.16f * fv);
        Draw.rrect(g, bx, by, bw, 22, 6, 0xFF0C1222);
        Draw.rrectLine(g, bx, by, bw, 22, 6, Draw.mix(0xFF2A3E6A, GOLD, fv));
        hit(H_FIELD, -1, bx, by, bw, 22);
        button(g, H_REDEEM, cx - 60, by + 30, 120, 20, "REDEEM", GOLD, !codeBox.getValue().isBlank(), true, mx, my, t, dt);
        if (!codeMsg.isEmpty()) {
            long since = System.currentTimeMillis() - codeAt;
            float a = Math.min(1f, since / 200f);
            float shake = codeOk ? 0f : (1f - Math.min(1f, since / 350f)) * 4f * (float) Math.sin(since / 18.0);
            Draw.textCenter(g, codeMsg, cx + Math.round(shake), by + 58, Draw.rgba(codeOk ? 0xFF6FE3A0 : 0xFFFF7A7A, a));
        }
        int iy = by + 70, iw = Math.min(pw - 24, 430);
        if (iy + 62 > py + ph - 32) {
            Draw.textCenter(g, Draw.fit("1 token per 5 min of play  ·  a key costs " + Crates.KEY_PRICE + " tokens, a crate takes " + Crates.KEYS_PER_OPEN + "  ·  free crate every 90 min", pw - 24), cx, by + 72, FAINT);
            return;
        }
        Draw.rrect(g, cx - iw / 2, iy, iw, 62, 8, 0xFF0B1124);
        Draw.rrectLine(g, cx - iw / 2, iy, iw, 62, 8, 0xFF1C2A48);
        Draw.bold(g, "HOW TO EARN", cx - iw / 2 + 10, iy + 6, GOLD);
        Draw.textRight(g, "opened " + Crates.opened(), cx + iw / 2 - 10, iy + 6, FAINT);
        String[] lines = {"1 Zeus token for every 5 minutes of active play. Standing still earns nothing.",
                "A key costs " + Crates.KEY_PRICE + " tokens and a crate takes " + Crates.KEYS_PER_OPEN + " keys. A free crate drops every 90 minutes of play.",
                "Already own the knife? You get tokens back instead, more for rarer drops."};
        for (int i = 0; i < lines.length; i++) Draw.text(g, Draw.fit("•  " + lines[i], iw - 20), cx - iw / 2 + 10, iy + 19 + i * 13, SUB);
    }

    // ------------------------------------------------------------------ history tab
    private static String ago(long at) {
        long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }

    private void dailyTab(GuiGraphics g, int mx, int my, float t, float dt) {
        int top = py + 62, bottom = py + ph - 34;
        int cw = Math.min(pw - 28, 460), cx = px + (pw - cw) / 2;
        Draw.bold(g, "DAILY QUESTS", cx, top + 2, GOLD);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long left = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
        Draw.textRight(g, "New quests in " + clock(left), cx + cw, top + 2, FAINT);
        List<com.zlorbie788.zeus.core.Quests.Quest> qs = com.zlorbie788.zeus.core.Quests.list();
        int ch = Math.max(24, Math.min(42, (bottom - top - 16 - 14) / 3 - 6));
        int y = top + 16;
        for (int i = 0; i < qs.size(); i++) {
            var q = qs.get(i);
            float in = outCubic((t - tabAt - i * 0.06f) / 0.35f);
            int yy = y + Math.round((1f - in) * 10f);
            int edge = q.done() ? 0xFF2F8F64 : 0xFF1C2A48;
            Draw.rrect(g, cx, yy, cw, ch, 8, q.done() ? 0xFF0C1D18 : 0xFF0B1124);
            Draw.rrectLine(g, cx, yy, cw, ch, 8, edge);
            String reward = "+" + q.kind().reward + " TOKENS";
            int rw = Draw.widthBold(reward) + 14;
            Draw.rrect(g, cx + cw - rw - 8, yy + 7, rw, 13, 6, q.done() ? 0xFF1F5A40 : 0xFF2A2210);
            Draw.boldCenter(g, reward, cx + cw - 8 - rw / 2, yy + 10, q.done() ? 0xFF8CF0C0 : GOLD);
            String count = q.progress() + " / " + q.target();
            int cnt = Draw.width(count);
            Draw.textRight(g, count, cx + cw - rw - 16, yy + 10, SUB);
            Draw.bold(g, Draw.fit((q.done() ? "\u2714 " : "") + q.text(), cw - rw - cnt - 36), cx + 10, yy + 9, q.done() ? 0xFF8CF0C0 : TEXT);
            int bw = cw - 20, bx = cx + 10, byy = yy + ch - Math.min(12, ch / 3);
            Draw.rrect(g, bx, byy, bw, 4, 2, 0xFF141C30);
            float f = q.fraction() * outCubic((t - tabAt - 0.1f) / 0.6f);
            if (f > 0) Draw.rrect(g, bx, byy, Math.max(3, Math.round(bw * f)), 4, 2, q.done() ? 0xFF5EE6A0 : GOLD);
            y += ch + 6;
        }
        boolean all = com.zlorbie788.zeus.core.Quests.allDone();
        String bonus = all ? "All done! Bonus paid: +" + com.zlorbie788.zeus.core.Quests.BONUS + " tokens"
                : "Finish all three for +" + com.zlorbie788.zeus.core.Quests.BONUS + " bonus tokens";
        if (y + 10 < bottom) Draw.textCenter(g, Draw.fit(bonus, cw), cx + cw / 2, y + 2, all ? 0xFF8CF0C0 : SUB);
        String st = Crates.streakClaimedToday() ? "Daily streak: day " + Crates.streak() + " claimed" : "Daily streak: play " + clock(Crates.streakPlayLeft()) + " more today";
        if (y + 26 < bottom) Draw.textCenter(g, Draw.fit(st, cw), cx + cw / 2, y + 16, FAINT);
    }

    private void historyTab(GuiGraphics g, int mx, int my, float t, float dt) {
        int top = py + 62, bottom = py + ph - 34;
        List<Crates.Drop> hist = Crates.history();
        // left: stats
        int sw = Math.max(120, Math.min(190, pw * 28 / 100));
        int sx = px + 12;
        Draw.rrect(g, sx, top, sw, bottom - top, 8, 0xFF0B1124);
        Draw.rrectLine(g, sx, top, sw, bottom - top, 8, 0xFF1C2A48);
        Draw.bold(g, "YOUR LUCK", sx + 8, top + 7, GOLD);
        Draw.text(g, Crates.opened() + " opened", sx + 8, top + 19, SUB);
        int[] by = Crates.dropsByRarity();
        int total = Math.max(1, hist.size());
        Rarity[] rs = Rarity.values();
        int ry = top + 34;
        for (int i = rs.length - 1; i >= 0; i--) {
            if (ry > bottom - 46) break;
            Draw.rrect(g, sx + 8, ry + 1, 6, 6, 3, rs[i].colour);
            Draw.text(g, rs[i].label, sx + 18, ry, 0xFFB0BEDC);
            Draw.textRight(g, by[i] + "", sx + sw - 8, ry, by[i] > 0 ? TEXT : FAINT);
            int bw = sw - 16;
            float f = by[i] / (float) total;
            Draw.rrect(g, sx + 8, ry + 10, bw, 2, 1, 0xFF141C30);
            if (f > 0) Draw.rrect(g, sx + 8, ry + 10, Math.max(2, Math.round(bw * outCubic((t - tabAt) / 0.6f) * f)), 2, 1, rs[i].colour);
            ry += 17;
        }
        // best drop: rarest, then most recent
        Crates.Drop best = null;
        for (Crates.Drop d : hist) if (best == null || Crates.rarityOf(d.knife()).ordinal() > Crates.rarityOf(best.knife()).ordinal()) best = d;
        if (best != null && bottom - ry > 40) {
            Draw.text(g, "BEST DROP", sx + 8, ry + 2, FAINT);
            int kind = kindOf(best.knife());
            int bh = Math.min(60, bottom - ry - 26);
            if (kind >= 0) KnifeArt.still(g, kind, sx + 6, ry + 12, sw - 12, bh);
            Draw.textCenter(g, Draw.fit(nameOf(best.knife()).replace(" \u00b7 ", " "), sw - 10), sx + sw / 2, ry + 14 + bh, Crates.rarityOf(best.knife()).colour);
        }
        // right: the list
        int lx = sx + sw + 10, lw = px + pw - 12 - lx;
        Draw.bold(g, "RECENT DROPS", lx, top, TEXT);
        Draw.textRight(g, hist.isEmpty() ? "" : "last " + hist.size(), lx + lw, top, FAINT);
        int gy = top + 13, gh = bottom - gy;
        if (hist.isEmpty()) {
            Draw.textCenter(g, "Nothing opened yet. Your drops will show up here.", lx + lw / 2, gy + gh / 2 - 4, FAINT);
            return;
        }
        int rowH = 24, gap = 3;
        int maxScroll = Math.max(0, hist.size() * (rowH + gap) - gh);
        histScroll = Math.max(0, Math.min(maxScroll, histScroll));
        histScrollShown = approach(histScrollShown, histScroll, 18f, dt);
        g.enableScissor(lx - 2, gy, lx + lw + 2, gy + gh);
        for (int i = 0; i < hist.size(); i++) {
            int y = gy + i * (rowH + gap) - Math.round(histScrollShown);
            if (y + rowH < gy || y > gy + gh) continue;
            Crates.Drop d = hist.get(i);
            Rarity r = Crates.rarityOf(d.knife());
            float e = outCubic((t - tabAt - i * 0.025f) / 0.25f);
            int ox = Math.round((1f - e) * 12f);
            boolean hot = in(mx, my, lx, y, lw, rowH) && my >= gy && my < gy + gh;
            float hv = hover(7000 + i, hot, dt);
            Draw.rrect(g, lx + ox, y, lw, rowH, 5, Draw.mix(0xFF0C1222, Draw.mix(0xFF0C1222, r.colour, 0.25f), hv));
            g.fill(lx + ox, y + 3, lx + ox + 2, y + rowH - 3, r.colour);
            int kind = kindOf(d.knife());
            if (kind >= 0) KnifeArt.still(g, kind, lx + ox + 6, y + 2, 44, rowH - 4);
            String n = nameOf(d.knife()).replace(" \u00b7 ", " ");
            Draw.bold(g, Draw.fit(n, lw - 170), lx + ox + 56, y + 4, TEXT);
            Crates.Crate cr = Crates.crate(d.crate());
            Draw.text(g, Draw.fit(r.label + (cr == null ? "" : "  \u00b7  " + cr.name), lw - 170), lx + ox + 56, y + 14, r.colour);
            Draw.textRight(g, ago(d.at()), lx + ox + lw - 8, y + 4, FAINT);
            Draw.textRight(g, d.duplicate() ? "duplicate" : "new", lx + ox + lw - 8, y + 14, d.duplicate() ? FAINT : 0xFF6FE3A0);
            if (e < 1f) Draw.rrect(g, lx + ox, y, lw, rowH, 5, Draw.rgba(PANEL, 1f - e));
            if (hot) hit(H_HIST, i, lx, y, lw, rowH);
        }
        g.disableScissor();
    }

    // ------------------------------------------------------------------ inspect
    private void openInspect(Item it) {
        inspect = it;
        inspectAt = now();
        inspectYaw = -0.35f;
        inspectSpin = 0f;
        placeCodeBox();
        snd(SoundEvents.ARMOR_EQUIP_GENERIC, 1.3f, 0.5f);
    }

    private int[] inspectBox() {
        int bw = Math.round(Math.min(width * 0.62f, 380)), bh = Math.round(Math.min(height * 0.45f, 200));
        return new int[] {width / 2 - bw / 2, height / 2 - bh / 2 - 26, bw, bh};
    }

    private void renderInspect(GuiGraphics g, int mx, int my, float t, float dt) {
        hits.clear();
        float u = t - inspectAt;
        float e = outCubic(u / 0.3f);
        g.fill(0, 0, width, height, Draw.rgba(0xF0030610, e));
        Item it = inspect;
        Rarity r = it.rarity();
        int[] b = inspectBox();
        Draw.glow(g, width / 2 - 60, b[1] + b[3] / 2 - 30, 120, 60, 30, r.colour, Math.round(Math.min(width, height) * 0.4f), 0.005f * e);
        // turn: drag with the mouse, or it drifts slowly on its own
        if (dragging) {
            inspectSpin = (float) (mx - dragLastX) * 0.035f / Math.max(dt, 1e-3f) * 0.016f;
            inspectYaw += (float) (mx - dragLastX) * 0.035f;
            dragLastX = mx;
        } else {
            inspectSpin = approach(inspectSpin, 0.35f * dt, 3f, dt);
            inspectYaw += inspectSpin;
        }
        float pop = 0.85f + 0.15f * outBack(u / 0.4f);
        int bw = Math.round(b[2] * pop), bh = Math.round(b[3] * pop);
        float bob = 3f * (float) Math.sin(u * 1.8f);
        KnifeHDGui.spin(g, Crates.hdOf(it.id()), width / 2 - bw / 2, Math.round(b[1] + b[3] / 2f - bh / 2f + bob), bw, bh, inspectYaw, 0.06f * (float) Math.sin(u * 0.7f));
        boolean own = Crates.owns(it.id());
        String full = nameOf(it.id());
        int ty = b[1] + b[3] + 8;
        float ta = outCubic((u - 0.1f) / 0.3f);
        Draw.boldCenterS(g, typeName(full).toUpperCase(Locale.ROOT), width / 2, ty, Draw.rgba(SUB, ta), 0.9f);
        Draw.boldCenterS(g, shortName(full).toUpperCase(Locale.ROOT), width / 2, ty + 11, Draw.rgba(TEXT, ta), 1.6f);
        Crate from = Crates.crateOf(it.id());
        String line = r.label + (from == null ? "  \u00b7  starter knife" : "  \u00b7  " + from.name);
        Draw.textCenter(g, line, width / 2, ty + 30, Draw.rgba(r.colour, ta));
        Draw.textCenter(g, own ? "drag to turn it" : "locked  \u00b7  open the " + (from == null ? "crates" : from.name) + " to get it", width / 2, ty + 42, Draw.rgba(own ? FAINT : 0xFFFF8A7A, ta));
        int w = 104, gap = 8;
        int y = Math.min(height - 24, ty + 56);
        float bt = outCubic((u - 0.2f) / 0.3f);
        int yy = y + Math.round((1f - bt) * 10f);
        if (own) {
            boolean held = Crates.hdOf(it.id()) == KnifeSwing.heldHd();
            button(g, H_EQUIP, width / 2 - w - gap / 2, yy, w, 20, held ? "IN HAND" : "EQUIP", r.colour, !held, true, mx, my, t, dt);
            button(g, H_DONE, width / 2 + gap / 2, yy, w, 20, "CLOSE", 0xFF5E73A0, true, false, mx, my, t, dt);
        } else {
            button(g, H_DONE, width / 2 - w / 2, yy, w, 20, "CLOSE", 0xFF5E73A0, true, false, mx, my, t, dt);
        }
    }

    private void inspectClick(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
            if (h[0] == H_EQUIP) {
                equip(inspect.id());
                inspect = null;
            } else if (h[0] == H_DONE) {
                inspect = null;
            }
            placeCodeBox();
            return;
        }
        int[] b = inspectBox();
        if (in(mx, my, b[0] - 20, b[1] - 20, b[2] + 40, b[3] + 40)) {
            dragging = true;
            dragLastX = mx;
        } else if (my < b[1] - 30 || my > height - 10) {
            inspect = null;
            placeCodeBox();
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    // ------------------------------------------------------------------ floaters (+1 token, +1 key)
    private void floater(String text, int colour, int x, int y, float t) {
        floats.add(new Object[] {text, colour, x, y, t});
    }

    private void floaters(GuiGraphics g, float t) {
        for (int i = floats.size() - 1; i >= 0; i--) {
            Object[] f = floats.get(i);
            float u = t - (float) f[4];
            if (u > 1.2f) {
                floats.remove(i);
                continue;
            }
            float a = u < 0.15f ? u / 0.15f : 1f - clamp01((u - 0.7f) / 0.5f);
            int y = (int) f[3] - Math.round(outCubic(u / 1.2f) * 14f);
            Draw.boldCenter(g, (String) f[0], (int) f[2], y, Draw.rgba((int) f[1], a));
        }
    }

    // ------------------------------------------------------------------ input
    private void setTab(int i) {
        if (i == tab) return;
        snd(SoundEvents.UI_BUTTON_CLICK, 1.2f, 0.5f);
        prevTab = tab;
        tab = i;
        tabAt = now();
        placeCodeBox();
        if (tab == T_REDEEM) setFocused(codeBox);
    }

    private void setSel(int i) {
        if (i == sel) return;
        snd(SoundEvents.UI_BUTTON_CLICK, 1.4f, 0.4f);
        sel = i;
        selAt = now();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (opening != null) {
            opening.click(mx, my);
            return true;
        }
        if (inspect != null) {
            inspectClick(mx, my);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
            switch (h[0]) {
                case H_TAB -> setTab(h[1]);
                case H_CRATE -> setSel(h[1]);
                case H_OPEN -> startOpening(Crates.CRATES.get(sel));
                case H_BUY -> {
                    int n = Crates.buyKeys(event.hasShiftDown() ? Crates.KEYS_PER_OPEN : 1);
                    if (n > 0) {
                        snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f);
                        say("Bought " + (n == 1 ? "a key" : n + " keys") + " for " + n * Crates.KEY_PRICE + " tokens");
                    }
                }
                case H_REDEEM -> redeem();
                case H_COLL -> openInspect(allItems().get(h[1]));
                case H_INSPECT -> openInspect(Crates.CRATES.get(sel).items.get(h[1]));
                case H_HIST -> {
                    var d = Crates.history().get(h[1]);
                    if (Crates.owns(d.knife())) openInspect(new Item(d.knife(), Crates.rarityOf(d.knife())));
                }
                case H_QUICK -> {
                    Crates.setQuick(!Crates.quick());
                    snd(SoundEvents.UI_BUTTON_CLICK, Crates.quick() ? 1.6f : 1.1f, 0.5f);
                }
                case H_CLOSE -> onClose();
                case H_FIELD -> {
                    setFocused(codeBox);
                    return super.mouseClicked(event, doubleClick);
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (inspect != null) return true;
        if (tab == T_COLL && opening == null) {
            collScroll -= (float) sy * 36f;
            return true;
        }
        if (tab == T_HIST && opening == null) {
            histScroll -= (float) sy * 30f;
            return true;
        }
        if (tab == T_CRATES && opening == null) {
            setSel(Math.floorMod(sel - (int) Math.signum(sy), Crates.CRATES.size()));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (opening != null) {
            if (key == 256) opening.escape();
            else if (key == 32 || key == 257) opening.skip();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        if (inspect != null) {
            if (key == 256 || key == 257) inspect = null;
            return true;
        }
        if (tab == T_REDEEM && (key == 257 || key == 335)) {
            redeem();
            return true;
        }
        if (tab == T_CRATES && codeBox != null && !codeBox.isFocused()) {
            if (key == 264) setSel((sel + 1) % Crates.CRATES.size());
            else if (key == 265) setSel(Math.floorMod(sel - 1, Crates.CRATES.size()));
            else if (key == 257 || key == 32) startOpening(Crates.CRATES.get(sel));
        }
        return super.keyPressed(event);
    }

    private void redeem() {
        Crates.Redeem r = Crates.redeem(codeBox.getValue());
        codeMsg = r.message();
        codeOk = r.ok();
        codeAt = System.currentTimeMillis();
        if (r.ok()) {
            codeBox.setValue("");
            snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
        } else {
            snd(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
    }

    private void say(String s) {
        flash = s;
        flashAt = System.currentTimeMillis();
    }

    private void equip(String id) {
        int hd = Crates.hdOf(id);
        if (hd < 0 || !Crates.owns(id)) return;
        KnifeSwing.equipHd(hd);
        snd(SoundEvents.ARMOR_EQUIP_IRON, 1.2f, 0.7f);
        say("Now holding " + nameOf(id).replace(" · ", " "));
    }

    /** A line in chat for the drops worth talking about (Divine and rarer). */
    private void announce(Crates.Result r) {
        try {
            if (r.item().rarity().ordinal() < Rarity.DIVINE.ordinal() || minecraft.gui == null) return;
            int col = r.item().rarity().colour & 0xFFFFFF;
            net.minecraft.network.chat.MutableComponent m = Component.literal("[Zeus Crates] ").withColor(0xF5C04A)
                    .append(Component.literal("You unboxed ").withColor(0xE9F1FF))
                    .append(Component.literal(nameOf(r.item().id()).replace(" \u00b7 ", " | ")).withColor(col))
                    .append(Component.literal(" (" + r.item().rarity().label + ")").withColor(col));
            minecraft.gui.getChat().addMessage(m);
        } catch (RuntimeException ignored) {
        }
    }

    private void startOpening(Crate c) {
        if (!Crates.canOpen(c)) return;
        Crates.Result res = Crates.open(c);
        if (res == null) return;
        opening = new Opening(c, res, lastCrateCx, lastCrateCy, lastCrateSize);
        placeCodeBox();
        snd(SoundEvents.CHEST_LOCKED, 1.3f, 0.7f);
    }

    // ------------------------------------------------------------------ the opening sequence
    private final class Opening {
        // timeline (seconds)
        // timeline (seconds); Quick open shortens every step
        final boolean fast = Crates.quick();
        final float FLY = fast ? 0.22f : 0.45f;       // crate flies from the panel to the middle
        final float RATTLE = fast ? 0.18f : 0.42f;    // shakes harder and harder
        final float BURST = 0.22f;                     // lid blows off
        final float SPIN0 = FLY + RATTLE + 0.05f;
        final float SPIN = fast ? 2.4f : 5.6f;        // reel runs and slows
        final float SETTLE = fast ? 0.25f : 0.40f;    // overshoot ticks back onto the winner
        final float HOLD = fast ? 0.30f : 0.55f;      // winner lifts, light beam
        final float MORPH = 0.50f;                     // knife flies out of the card into the reveal

        final Crate crate;
        final Crates.Result res;
        final List<Item> reel = new ArrayList<>();
        final int win;
        final float land;
        final float over;
        final int fromX, fromY, fromSize;
        final long start = System.nanoTime();
        float skipBy;
        int lastCard = Integer.MIN_VALUE;
        long lastTick;
        boolean burstSound, landSound, revealSound, beamSound;
        final float[][] sparks = new float[90][6];
        final float[][] confetti;

        Opening(Crate c, Crates.Result r, int fromX, int fromY, int fromSize) {
            crate = c;
            res = r;
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromSize = Math.max(40, fromSize);
            Random rnd = new Random();
            for (int i = 0; i < 64; i++) reel.add(Crates.roll(c));
            win = 56;
            reel.set(win, r.item());
            land = (rnd.nextFloat() - 0.5f) * 0.70f;
            over = 0.14f + rnd.nextFloat() * 0.16f;
            for (float[] s : sparks) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float v = 70 + rnd.nextFloat() * 260;
                s[0] = (float) Math.cos(a) * v;
                s[1] = (float) Math.sin(a) * v - 50;
                s[2] = 1 + rnd.nextFloat() * 2.2f;
                s[3] = 0.6f + rnd.nextFloat() * 1.0f;
                s[4] = rnd.nextFloat();
                s[5] = rnd.nextFloat() * 6.28f;
            }
            boolean gold = r.item().rarity() == Rarity.OLYMPIAN;
            confetti = new float[gold ? 70 : 0][6];
            for (float[] q : confetti) {
                q[0] = rnd.nextFloat();
                q[1] = rnd.nextFloat() * 1.5f;
                q[2] = 30 + rnd.nextFloat() * 50;
                q[3] = rnd.nextFloat() * 6.28f;
                q[4] = 2 + rnd.nextFloat() * 3;
                q[5] = rnd.nextFloat();
            }
        }

        float t() {
            return (System.nanoTime() - start) / 1e9f + skipBy;
        }

        float landedAt() {
            return SPIN0 + SPIN;
        }

        float revealAt() {
            return SPIN0 + SPIN + SETTLE + HOLD;
        }

        void skip() {
            float t = t();
            if (t < revealAt()) skipBy += revealAt() - t;
        }

        void escape() {
            if (t() < revealAt()) skip();
            else close();
        }

        void close() {
            opening = null;
            openedAt = now();
            placeCodeBox();
        }

        /** Reel position in cards: races, slows like a real wheel, runs a little past and ticks back. */
        float pos(float t) {
            float s = t - SPIN0;
            if (s <= 0) return 0;
            float target = win + land;
            if (s < SPIN) {
                float k = s / SPIN;
                return (target + over) * (1f - (float) Math.pow(1f - k, 4.4f));
            }
            float b = inOutCubic((s - SPIN) / SETTLE);
            return target + over * (1f - b);
        }

        float vel(float t) {
            return (pos(t + 0.02f) - pos(t - 0.02f)) / 0.04f;
        }

        void click(int mx, int my) {
            float t = t();
            if (t < revealAt()) {
                skip();
                return;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                int[] h = hits.get(i);
                if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
                if (h[0] == H_EQUIP) {
                    equip(res.item().id());
                    close();
                } else if (h[0] == H_AGAIN) {
                    close();
                    startOpening(crate);
                } else if (h[0] == H_DONE) {
                    close();
                }
                return;
            }
        }

        // reel geometry shared by the reel and the morph into the reveal
        int cw() {
            return Math.max(64, Math.min(100, width / 7));
        }

        int ch() {
            return Math.round(cw() * 1.22f);
        }

        void render(GuiGraphics g, int mx, int my, float clock, float dt) {
            float t = t();
            hits.clear();
            float dim = clamp01(t / 0.25f);
            g.fill(0, 0, width, height, Draw.rgba(0xF2020408, dim));
            if (t < SPIN0 + 0.35f) intro(g, t, clock);
            if (t >= SPIN0 - 0.15f && t < revealAt() + 0.4f) reelView(g, t, clock);
            if (t >= revealAt()) reveal(g, mx, my, t - revealAt(), clock, dt);
            else Draw.textCenter(g, "click or press space to skip", width / 2, height - 18, 0x605E73A0);
        }

        void intro(GuiGraphics g, float t, float clock) {
            int cx = width / 2, cy = height / 2;
            float fly = inOutCubic(t / FLY);
            float x = fromX + (cx - fromX) * fly, y = fromY + (cy - fromY) * fly;
            int size = Math.round(fromSize + (Math.min(150, height / 2.2f) - fromSize) * fly);
            float rattle = clamp01((t - FLY) / RATTLE);
            float shake = rattle * rattle * 6f;
            float lid = clamp01((t - FLY - RATTLE) / BURST);
            float fade = 1f - clamp01((t - SPIN0) / 0.35f);
            if (rattle > 0 && !burstSound && lid <= 0) {
                // a rattle of clicks that speeds up
                int n = (int) (rattle * rattle * 10);
                if (n > lastCard) {
                    lastCard = n;
                    snd(SoundEvents.CHEST_LOCKED, 1.2f + n * 0.05f, 0.25f + rattle * 0.3f);
                }
            }
            if (!burstSound && lid > 0.05f) {
                burstSound = true;
                lastCard = Integer.MIN_VALUE;
                snd(SoundEvents.ENDER_CHEST_OPEN, 1.1f, 0.8f);
                snd(SoundEvents.FIREWORK_ROCKET_BLAST, 1.3f, 0.5f);
            }
            if (fade <= 0f) return;
            Draw.glow(g, Math.round(x) - size / 3, Math.round(y) - size / 4, size * 2 / 3, size / 2, 30, crate.colour, Math.max(20, size / 2), (0.02f + 0.05f * rattle + 0.1f * lid) * fade);
            drawCrate(g, crate, x, y, size, clock, lid, lid > 0 ? 0f : shake, rattle * 0.5f + lid);
            if (lid > 0f) {
                // shockwave ring and a flash of the crate's colour
                float since = t - FLY - RATTLE;
                float rr = 10 + since * size * 3.2f;
                float ra = 1f - clamp01(since / 0.6f);
                ring(g, x, y, rr, Draw.rgba(Draw.lighten(crate.colour, 0.4f), 0.7f * ra), 2);
                g.fill(0, 0, width, height, Draw.rgba(Draw.lighten(crate.colour, 0.6f), 0.30f * (1f - lid) * lid * 4f * fade));
            }
        }

        void ring(GuiGraphics g, float cx, float cy, float r, int colour, int thick) {
            int ri = Math.max(2, Math.round(r));
            for (int k = 0; k < thick; k++) Draw.rrectLine(g, Math.round(cx) - ri - k, Math.round(cy) - ri - k, (ri + k) * 2, (ri + k) * 2, ri + k, colour);
        }

        void reelView(GuiGraphics g, float t, float clock) {
            int cw = cw(), ch = ch(), gap = 6;
            int step = cw + gap;
            float cy = height / 2f - ch / 2f;
            float slide = outCubic((t - (SPIN0 - 0.15f)) / 0.4f);
            float pos = pos(t);
            float out = clamp01((t - revealAt()) / 0.3f);
            float a = slide * (1f - out);
            if (a <= 0.01f) return;
            int under = (int) Math.floor(pos + 0.5f);
            if (under != lastCard) {
                long n = System.nanoTime();
                if (lastCard != Integer.MIN_VALUE && n - lastTick > 26_000_000L && t < landedAt() + SETTLE + 0.05f) {
                    float speed = Math.abs(vel(t));
                    snd(SoundEvents.UI_BUTTON_CLICK, 1.45f + Math.min(0.55f, speed / 55f), 0.20f + Math.min(0.15f, speed / 200f));
                    lastTick = n;
                }
                lastCard = under;
            }
            int railY = Math.round(cy) - 16, railH = ch + 32;
            g.fill(0, railY, width, railY + railH, Draw.rgba(0xF00A0E1A, a));
            g.fill(0, railY, width, railY + 1, Draw.rgba(crate.colour, 0.6f * a));
            g.fill(0, railY + railH - 1, width, railY + railH, Draw.rgba(crate.colour, 0.6f * a));
            boolean landed = t >= landedAt() + SETTLE;
            float hold = clamp01((t - (landedAt() + SETTLE)) / HOLD);
            float speed = Math.abs(vel(t));
            float slideX = (1f - slide) * width * 0.5f;
            g.enableScissor(0, railY, width, railY + railH);
            int first = (int) Math.floor(pos - (width / 2f) / step) - 1;
            for (int i = Math.max(0, first); i < Math.min(reel.size(), first + width / step + 4); i++) {
                if (landed && i == win) continue;          // drawn on top, outside the clip
                float fx = width / 2f + (i - pos) * step - cw / 2f + slideX;
                // cards near the marker grow a little and brighten
                float near = Math.max(0f, 1f - Math.abs(i - pos));
                float s = 1f + 0.06f * near * (speed < 18 ? 1f : 0.4f);
                float dimK = landed ? 1f - 0.7f * outCubic(hold * 2f) : 1f;
                g.pose().pushMatrix();
                g.pose().translate(fx + cw / 2f, cy + ch / 2f + (landed ? outCubic(hold * 1.5f) * 8f : 0f));
                g.pose().scale(s, s);
                g.pose().translate(-cw / 2f, -ch / 2f);
                card(g, reel.get(i), 0, 0, cw, ch, false, false, 0f, 0f);
                float shade = 1f - Math.min(1f, a * dimK);
                if (shade > 0.01f) Draw.rrect(g, 0, 0, cw, ch, 5, Draw.rgba(0xFF04060C, shade));
                g.pose().popMatrix();
            }
            // speed streaks and soft dark edges
            if (speed > 8) {
                float k = Math.min(1f, (speed - 8) / 30f);
                Random r = new Random((long) (pos * 3));
                for (int s = 0; s < 16; s++) {
                    int yy = Math.round(cy) + r.nextInt(ch);
                    int len = 40 + r.nextInt(140);
                    int xx = r.nextInt(width);
                    g.fill(xx, yy, xx + len, yy + 1, Draw.rgba(0xFFFFFFFF, 0.10f * k * a));
                }
            }
            for (int e = 0; e < 10; e++) {
                float k = (1f - e / 10f);
                int col = Draw.rgba(0xFF04060C, 0.10f * k * a);
                int w = Math.max(1, width / 40);
                g.fill(e * w, railY, (e + 1) * w, railY + railH, col);
                g.fill(width - (e + 1) * w, railY, width - e * w, railY + railH, col);
            }
            g.disableScissor();
            // the marker
            int mxl = width / 2;
            Draw.glow(g, mxl - 1, railY - 6, 2, railH + 12, 1, GOLD, 8, 0.25f * a);
            g.fill(mxl - 1, railY - 6, mxl + 1, railY + railH + 6, Draw.rgba(GOLD, a * (1f - 0.8f * hold)));
            for (int k = 0; k < 6; k++) {
                g.fill(mxl - 6 + k, railY - 8 + k, mxl + 6 - k, railY - 7 + k, Draw.rgba(GOLD, a));
                g.fill(mxl - 6 + k, railY + railH + 7 - k, mxl + 6 - k, railY + railH + 8 - k, Draw.rgba(GOLD, a));
            }
            Draw.boldCenterS(g, crate.name.toUpperCase(Locale.ROOT), width / 2, railY - 30, Draw.rgba(crate.colour, a), 1.3f);
            // the winner: lifts out of the reel with a beam of its rarity's light
            if (landed) {
                if (!landSound) {
                    landSound = true;
                    snd(SoundEvents.NOTE_BLOCK_CHIME, 1.6f, 0.7f);
                }
                Rarity rar = res.item().rarity();
                if (!beamSound && rar.ordinal() >= Rarity.DIVINE.ordinal()) {
                    beamSound = true;
                    snd(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.5f);
                }
                float fx = width / 2f + (win - pos) * step - cw / 2f;
                float lift = outBack(hold * 1.3f);
                float s = 1f + 0.22f * lift;
                int col = rar.colour;
                float bw = cw * (0.3f + 0.9f * outCubic(hold));
                Draw.glow(g, Math.round(fx + cw / 2f - bw / 2f), 0, Math.round(bw), height, 4, col, 26, 0.035f * outCubic(hold * 2f) * a);
                g.fill(Math.round(fx + cw / 2f - 1), 0, Math.round(fx + cw / 2f + 1), height, Draw.rgba(Draw.lighten(col, 0.5f), 0.5f * outCubic(hold * 2f) * a));
                g.pose().pushMatrix();
                g.pose().translate(fx + cw / 2f, cy + ch / 2f - lift * 10f);
                g.pose().scale(s, s);
                g.pose().translate(-cw / 2f, -ch / 2f);
                float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 10f);
                Draw.glow(g, 0, 0, cw, ch, 6, col, 16, (0.12f + 0.10f * pulse) * a);
                card(g, reel.get(win), 0, 0, cw, ch, false, false, 0f, 0f);
                g.pose().popMatrix();
            }
        }

        void reveal(GuiGraphics g, int mx, int my, float u, float clock, float dt) {
            Rarity rar = res.item().rarity();
            int col = rar.colour;
            boolean big = rar.ordinal() >= Rarity.TITAN.ordinal();
            if (!revealSound) {
                announce(res);
            }
            if (!revealSound) {
                revealSound = true;
                if (big) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    snd(SoundEvents.TOTEM_USE, 1.4f, 0.35f);
                } else if (rar.ordinal() >= Rarity.DIVINE.ordinal()) {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.1f, 0.8f);
                    snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
                } else {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
                }
            }
            int below = 6 + 40 + 6 + 10 + 6 + 20;
            int bhMax = Math.max(60, Math.min(Math.round(height * 0.42f), Math.min(190, height - below - 34)));
            int bwMax = Math.round(Math.min(width * 0.62f, 360));
            float cx = width / 2f, cy = (height - (bhMax + below)) / 2f + bhMax / 2f + 6;
            // shake on the rarest drops
            float shk = big ? (1f - clamp01(u / 0.5f)) * 5f : 0f;
            g.pose().pushMatrix();
            g.pose().translate(shk * (float) Math.sin(u * 70), shk * (float) Math.cos(u * 57));
            float e = outCubic(u / 0.45f);
            if (u < 0.25f) g.fill(0, 0, width, height, Draw.rgba(0xFFFFFFFF, 0.45f * (1f - u / 0.25f)));
            // soft rarity light and turning rays (two layers on the rarest drops)
            Draw.glow(g, Math.round(cx) - 60, Math.round(cy) - 30, 120, 60, 30, col, Math.round(Math.min(width, height) * 0.45f), 0.006f * e);
            int len = Math.round(Math.max(width, height) * 0.75f);
            int rays = 24;
            for (int layer = 0; layer < (big ? 2 : 1); layer++) {
                float rot = (layer == 0 ? 1 : -0.6f) * clock * 0.22f;
                for (int i = 0; i < rays; i++) {
                    double ang = rot + (i + layer * 0.5) * Math.PI * 2 / rays;
                    float alpha = (i % 2 == 0 ? 0.12f : 0.06f) * e * (layer == 0 ? 1f : 0.6f);
                    for (int k = -2; k <= 2; k++) {
                        double aa = ang + k * 0.017;
                        Draw.line(g, cx, cy, cx + (float) Math.cos(aa) * len, cy + (float) Math.sin(aa) * len, 3f, Draw.rgba(col, alpha * (1f - Math.abs(k) * 0.22f)));
                    }
                }
            }
            // shockwave rings
            for (int r = 0; r < (rar.ordinal() >= Rarity.DIVINE.ordinal() ? 2 : 1); r++) {
                float ru = u - r * 0.14f;
                if (ru > 0 && ru < 0.8f) ring(g, cx, cy, 20 + ru * Math.max(width, height) * 0.6f, Draw.rgba(Draw.lighten(col, 0.4f), 0.6f * (1f - ru / 0.8f)), 2);
            }
            // sparks thrown out by the burst, then falling and twinkling
            for (float[] s : sparks) {
                float life = u / s[3];
                if (life >= 1f) continue;
                float x = cx + s[0] * u, y = cy + s[1] * u + 170f * u * u;
                float tw = 0.6f + 0.4f * (float) Math.sin(clock * 18 + s[5]);
                int sz = Math.max(1, Math.round(s[2]));
                g.fill(Math.round(x), Math.round(y), Math.round(x) + sz, Math.round(y) + sz,
                        Draw.rgba(s[4] < 0.5f ? Draw.lighten(col, 0.5f) : 0xFFFFF4D0, (1f - life) * tw));
            }
            // gold confetti for an Olympian
            for (float[] q : confetti) {
                float fall = (u * 0.35f + q[1]) % 1.6f;
                float x = q[0] * width + (float) Math.sin(u * 2 + q[3]) * 14;
                float y = -20 + fall * (height + 40) / 1.5f;
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().rotate(u * 3 + q[3]);
                int w = Math.round(q[4]), h = Math.max(1, Math.round(q[4] * 0.5f * Math.abs((float) Math.cos(u * 6 + q[3]))));
                g.fill(-w, -h, w, h, q[5] < 0.5f ? 0xFFFFD66A : 0xFFFFF2C0);
                g.pose().popMatrix();
            }
            // the knife flies out of the winning card into the middle, turning as it comes
            float m = outCubic(u / MORPH);
            int cw = cw(), ch = ch();
            float fromW = cw * 1.22f - 6, fromH = ch * 1.22f - 26;
            float fromCx = width / 2f - land * (cw + 6), fromCy = height / 2f - 10;
            float bw = fromW + (bwMax - fromW) * m, bh = fromH + (bhMax - fromH) * m;
            float kx = fromCx + (cx - fromCx) * m, ky = fromCy + (cy - fromCy) * m;
            float settle = clamp01((u - MORPH) / 0.6f);
            float yaw = -0.35f - (1f - outCubic(u / 0.9f)) * 3.14159f + 0.5f * (float) Math.sin(u * 1.1f) * settle;
            float tilt = 0.10f * (float) Math.sin(u * 0.8f) * settle;
            float bob = 4f * (float) Math.sin(u * 2f) * settle;
            KnifeHDGui.spin(g, Crates.hdOf(res.item().id()), Math.round(kx - bw / 2f), Math.round(ky - bh / 2f + bob), Math.round(bw), Math.round(bh), yaw, tilt);
            // rarity chip pops in above
            String rl = rar.label.toUpperCase(Locale.ROOT);
            float ra = outBack((u - 0.35f) / 0.35f);
            if (ra > 0) {
                int rw = Draw.widthBold(rl) + 20;
                int chipY = Math.max(4, Math.round(cy - bhMax / 2f - 16));
                g.pose().pushMatrix();
                g.pose().translate(cx, chipY + 7);
                g.pose().scale(ra, ra);
                Draw.rrect(g, -rw / 2, -7, rw, 14, 7, col);
                Draw.boldCenter(g, rl, 0, -4, 0xFF0A0A12);
                g.pose().popMatrix();
            }
            // banner slides up, the name types itself in, a shine runs across
            int ty = Math.round(cy + bhMax / 2f + 6);
            String full = nameOf(res.item().id());
            float ta = outCubic((u - 0.30f) / 0.35f);
            int slide = Math.round((1f - ta) * 12f);
            int bw2 = Math.max(Draw.widthBoldS(shortName(full).toUpperCase(Locale.ROOT), 1.6f), 160) + 40;
            if (ta > 0) {
                Draw.rrect(g, Math.round(cx) - bw2 / 2, ty + slide, bw2, 40, 6, Draw.rgba(0xE0080A14, ta), Draw.rgba(Draw.mix(0xFF080A14, col, 0.25f), ta));
                g.fill(Math.round(cx) - bw2 / 2 + 6, ty + slide, Math.round(cx) + bw2 / 2 - 6, ty + slide + 2, Draw.rgba(col, ta));
                Draw.boldCenterS(g, typeName(full).toUpperCase(Locale.ROOT), Math.round(cx), ty + 6 + slide, Draw.rgba(SUB, ta), 0.9f);
                String nm = shortName(full).toUpperCase(Locale.ROOT);
                int shown = Math.min(nm.length(), (int) Math.ceil(nm.length() * clamp01((u - 0.4f) / 0.35f)));
                Draw.boldCenterS(g, nm.substring(0, shown), Math.round(cx), ty + 18 + slide, Draw.rgba(TEXT, ta), 1.6f);
                float sh = (u - 0.9f) / 0.7f;
                if (sh > 0 && sh < 1.6f) Draw.sheen(g, Math.round(cx) - bw2 / 2, ty + slide, bw2, 40, 0x50FFFFFF, sh);
            }
            // new or duplicate stamps in
            String badge = res.duplicate() ? "DUPLICATE  ·  +" + res.refund() + " TOKEN" + (res.refund() == 1 ? "" : "S") : "NEW!";
            int bc = res.duplicate() ? 0xFF8EA2CC : 0xFF6FE3A0;
            float ba = clamp01((u - 0.75f) / 0.2f);
            if (ba > 0) {
                float bs = 1f + 0.6f * (1f - outCubic(ba));
                Draw.boldCenterS(g, badge, Math.round(cx), ty + 46, Draw.rgba(bc, ba), bs);
            }
            g.pose().popMatrix();
            // buttons rise in one after another
            boolean again = Crates.canOpen(crate);
            int w = 104, gap = 8;
            int n = again ? 3 : 2;
            int x = Math.round(cx) - (n * w + (n - 1) * gap) / 2;
            int y = Math.min(height - 24, ty + 62);
            for (int b = 0; b < n; b++) {
                float bt = outCubic((u - 0.9f - b * 0.07f) / 0.3f);
                if (bt > 0) {
                    int yy = y + Math.round((1f - bt) * 14f);
                    if (b == 0) button(g, H_EQUIP, x, yy, w, 20, "EQUIP", col, true, true, mx, my, clock, dt);
                    else if (again && b == 1) button(g, H_AGAIN, x, yy, w, 20, "OPEN ANOTHER", crate.colour, true, false, mx, my, clock, dt);
                    else button(g, H_DONE, x, yy, w, 20, "DONE", 0xFF5E73A0, true, false, mx, my, clock, dt);
                }
                x += w + gap;
            }
        }
    }
}
