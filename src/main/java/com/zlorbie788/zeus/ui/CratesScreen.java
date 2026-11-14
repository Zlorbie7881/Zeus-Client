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
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Zeus Crates: pick a crate, see what is inside and the odds, open it with a key on a spinning reel,
 * and watch the knife come out. Also the collection of every knife and the promo code box.
 */
public final class CratesScreen extends Screen {
    private static final String[] TABS = {"CRATES", "COLLECTION", "REDEEM"};
    private static final int GOLD = 0xFFF5C04A;
    private static final int INK = 0xFF070A14;
    private static final int TEXT = 0xFFE9F1FF;
    private static final int SUB = 0xFF8EA2CC;
    private static final int FAINT = 0xFF5E73A0;

    private final Screen parent;
    private final long born = System.nanoTime();
    private int tab;
    private int sel;
    private int px, py, pw, ph;
    private float collScroll;
    private EditBox codeBox;
    private String codeMsg = "";
    private boolean codeOk;
    private long codeAt;
    private String flash = "";
    private long flashAt;
    private Opening opening;

    // hit boxes rebuilt every frame
    private final List<int[]> hits = new ArrayList<>();
    private static final int H_TAB = 1, H_CRATE = 2, H_OPEN = 3, H_BUY = 4, H_REDEEM = 5, H_EQUIP = 6, H_AGAIN = 7, H_DONE = 8,
            H_COLL = 9, H_CLOSE = 10, H_SKIP = 11;

    public CratesScreen(Screen parent) {
        super(Component.literal("Zeus Crates"));
        this.parent = parent;
        Crates.load();
        // start on a crate that can be opened, if there is one
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
        pw = Math.min(width - 16, 660);
        ph = Math.min(height - 16, 370);
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
        codeBox.setY(py + 96);
        codeBox.setWidth(bw - 16);
        codeBox.visible = tab == 2 && opening == null;
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

    private void button(GuiGraphics g, int kind, int x, int y, int w, int h, String label, int colour, boolean enabled, boolean primary, int mx, int my, float t) {
        boolean hot = enabled && in(mx, my, x, y, w, h);
        if (primary && enabled) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.2f);
            Draw.glow(g, x, y, w, h, 6, colour, 7, 0.20f + 0.18f * pulse + (hot ? 0.2f : 0f));
            Draw.rrect(g, x, y, w, h, 6, Draw.lighten(colour, hot ? 0.18f : 0.04f), Draw.darken(colour, 0.25f));
            Draw.sheen(g, x, y, w, h, 0x60FFFFFF, (t * 0.45f) % 1.6f);
            Draw.boldCenter(g, label, x + w / 2, y + (h - 8) / 2, 0xFF1A1206);
        } else {
            int bg = !enabled ? 0xFF10141E : hot ? 0xFF1E2A48 : 0xFF141C30;
            Draw.rrect(g, x, y, w, h, 6, bg);
            Draw.rrectLine(g, x, y, w, h, 6, !enabled ? 0xFF1E2638 : hot ? Draw.lighten(colour, 0.2f) : Draw.darken(colour, 0.35f));
            Draw.boldCenter(g, label, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : 0xFF4A5670);
        }
        if (enabled) hit(kind, 0, x, y, w, h);
    }

    /** A Zeus crate drawn from boxes: body, lid, straps, a glowing emblem. */
    private void drawCrate(GuiGraphics g, Crate c, int cx, int cy, int size, float t, float lid, float shake) {
        int w = size, h = Math.round(size * 0.72f);
        int x = cx - w / 2 + Math.round(shake * (float) Math.sin(t * 61));
        int y = cy - h / 2 + Math.round(shake * 0.6f * (float) Math.cos(t * 53));
        int r = Math.max(2, size / 14);
        Draw.shadow(g, x, y + h / 6, w, h * 5 / 6, r, Math.max(3, size / 10), 0.55f);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 2.0f);
        Draw.glow(g, x, y, w, h, r, c.colour, Math.max(3, size / 9), 0.10f + 0.10f * breath);
        int bodyY = y + h / 5;
        Draw.rrect(g, x, bodyY, w, h - h / 5, r, Draw.mix(c.dark, 0xFF1A2030, 0.35f), Draw.darken(c.dark, 0.45f));
        Draw.rrectLine(g, x, bodyY, w, h - h / 5, r, Draw.darken(c.colour, 0.1f));
        // straps
        int sw = Math.max(3, w / 9);
        for (int s = 0; s < 2; s++) {
            int sx = s == 0 ? x + w / 5 - sw / 2 : x + w * 4 / 5 - sw / 2;
            g.fill(sx, bodyY + 1, sx + sw, y + h - 1, Draw.darken(c.colour, 0.55f));
            g.fill(sx, bodyY + 1, sx + 1, y + h - 1, Draw.rgba(c.colour, 0.6f));
            g.fill(sx + sw - 1, bodyY + 1, sx + sw, y + h - 1, Draw.rgba(c.colour, 0.6f));
        }
        // emblem
        int er = Math.max(4, size / 7);
        int ecx = x + w / 2, ecy = bodyY + (h - h / 5) / 2 + 2;
        Draw.glow(g, ecx - er, ecy - er, er * 2, er * 2, er, c.colour, Math.max(2, er / 2), 0.25f + 0.25f * breath);
        Draw.rrect(g, ecx - er, ecy - er, er * 2, er * 2, er, Draw.darken(c.colour, 0.55f));
        Draw.rrectLine(g, ecx - er, ecy - er, er * 2, er * 2, er, c.colour);
        float sc = Math.max(0.6f, er / 6f);
        Draw.boldCenterS(g, "Z", ecx, ecy - Math.round(4 * sc), Draw.lighten(c.colour, 0.3f), sc);
        // lid: lifts and tilts when opening
        int lh = Math.max(4, h / 4);
        int ly = y - Math.round(lid * size * 0.35f);
        int lx = x - Math.max(2, size / 30) - Math.round(lid * size * 0.08f);
        int lw = w + 2 * Math.max(2, size / 30);
        if (lid > 0.01f) {
            Draw.glow(g, x, bodyY - 2, w, 4, 2, Draw.lighten(c.colour, 0.5f), Math.max(4, size / 6), Math.min(1f, lid * 2f) * 0.9f);
            g.fill(x + 3, bodyY - 1, x + w - 3, bodyY + 2, Draw.rgba(0xFFFFF6D8, Math.min(1f, lid * 3f)));
        }
        Draw.rrect(g, lx, ly, lw, lh, r, Draw.mix(c.dark, 0xFF2A3044, 0.5f), Draw.darken(c.dark, 0.2f));
        Draw.rrectLine(g, lx, ly, lw, lh, r, c.colour);
        g.fill(lx + lw / 2 - Math.max(3, w / 12), ly + lh - 2, lx + lw / 2 + Math.max(3, w / 12), ly + lh + Math.max(3, h / 9), c.colour);
    }

    private static String shortName(String full) {
        int dot = full.lastIndexOf('·');
        return dot >= 0 ? full.substring(dot + 1).trim() : full;
    }

    private static String typeName(String full) {
        int dot = full.indexOf('·');
        return dot >= 0 ? full.substring(0, dot).trim() : full;
    }

    private static String nameOf(String id) {
        int hd = Crates.hdOf(id);
        KnifeHD.Model m = hd >= 0 ? KnifeHD.model(hd) : null;
        return m == null ? id : m.name;
    }

    private static int kindOf(String id) {
        int hd = Crates.hdOf(id);
        return hd < 0 ? -1 : KnifeArt.allKnives().length - KnifeArt.HD_COUNT + hd;
    }

    /** One knife card: dark glass tinted with the rarity, the knife, its name, a rarity strip. */
    private void card(GuiGraphics g, Item it, int x, int y, int w, int h, float time, boolean owned, boolean locked, boolean big, float alpha) {
        int rc = it.rarity().colour;
        Draw.rrect(g, x, y, w, h, 5, Draw.rgba(Draw.mix(0xFF0C1020, rc, 0.22f), alpha), Draw.rgba(0xFF080B16, alpha));
        Draw.rrectLine(g, x, y, w, h, 5, Draw.rgba(Draw.darken(rc, 0.35f), alpha));
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 2, Draw.rgba(rc, alpha));
        int kind = kindOf(it.id());
        boolean named = h >= 44;
        if (kind >= 0 && alpha > 0.3f) KnifeArt.showcase(g, kind, 0, x + 3, y + 3, w - 6, h - (named ? (big ? 26 : 20) : 8), time, big);
        if (locked) {
            Draw.rrect(g, x, y, w, h, 5, 0xB0060810);
            Draw.boldCenter(g, "?", x + w / 2, y + h / 2 - 10, 0x80A0B0D0);
        }
        String n = shortName(nameOf(it.id()));
        if (named) Draw.textCenter(g, Draw.fit(n, w - 6), x + w / 2, y + h - 15, Draw.rgba(locked ? 0xFF5E6A88 : TEXT, alpha));
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
        g.fill(0, 0, width, height, 0xC0030610);
        // drifting light behind the panel
        int sc = Crates.CRATES.get(sel).colour;
        Draw.glow(g, px + pw / 4, py + ph / 3, pw / 2, ph / 3, 40, sc, 30, 0.05f);
        Draw.shadow(g, px, py, pw, ph, 10, 12, 0.6f);
        Draw.rrect(g, px, py, pw, ph, 10, 0xF4080C18, 0xF4050812);
        Draw.rrectLine(g, px, py, pw, ph, 10, 0xFF2A3E6A);

        header(g, mx, my, t);
        if (tab == 0) cratesTab(g, mx, my, t);
        else if (tab == 1) collectionTab(g, mx, my, t);
        else redeemTab(g, mx, my, t);
        footer(g, mx, my, t);

        if (opening == null) super.render(g, mx, my, partial);
        if (opening != null) opening.render(g, mx, my, t);

        if (!flash.isEmpty()) {
            float a = 1f - Math.max(0f, (System.currentTimeMillis() - flashAt - 2200) / 600f);
            if (a <= 0) flash = "";
            else Draw.toast(g, minecraft, width / 2, py + ph - 52, flash, true, a);
        }
    }

    private void header(GuiGraphics g, int mx, int my, float t) {
        Draw.boldS(g, "ZEUS", px + 14, py + 12, GOLD, 1.25f);
        Draw.boldS(g, "CRATES", px + 14 + Draw.widthBoldS("ZEUS ", 1.25f), py + 12, TEXT, 1.25f);
        // token and key chips
        String tk = Crates.tokens() + "";
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
        Draw.bold(g, Crates.keys() + "", kx + 24, py + 14, TEXT);
        Draw.text(g, "KEYS", kx + 30 + Draw.widthBold(Crates.keys() + ""), py + 14, SUB);
        // tabs
        int x = px + 14;
        for (int i = 0; i < TABS.length; i++) {
            int w = Draw.widthBold(TABS[i]) + 18;
            boolean on = i == tab, hot = in(mx, my, x, py + 34, w, 16);
            if (on) {
                g.fill(x + 4, py + 50, x + w - 4, py + 52, GOLD);
                Draw.glow(g, x + 4, py + 50, w - 8, 2, 1, GOLD, 4, 0.35f);
            }
            Draw.boldCenter(g, TABS[i], x + w / 2, py + 38, on ? TEXT : hot ? 0xFFC0CCE8 : FAINT);
            hit(H_TAB, i, x, py + 34, w, 16);
            x += w + 6;
        }
        g.fill(px + 12, py + 53, px + pw - 12, py + 54, 0xFF18223A);
        hit(H_CLOSE, 0, px + pw - 14 - 14, py + 34, 14, 14);
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
        Draw.rrect(g, bx, y + 3, Math.max(2, Math.round(bw * Crates.tokenProgress())), 5, 2, GOLD);
        String d = "Free crate drop in " + clock(Crates.nextDropIn());
        Draw.textRight(g, d, px + pw - 14, y + 2, FAINT);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ------------------------------------------------------------------ crates tab
    private void cratesTab(GuiGraphics g, int mx, int my, float t) {
        int top = py + 62, bottom = py + ph - 34;
        boolean small = pw < 560;
        int lw = small ? 96 : Math.max(120, pw * 24 / 100);
        int rowH = Math.max(18, Math.min(40, (bottom - top) / Crates.CRATES.size()));
        for (int i = 0; i < Crates.CRATES.size(); i++) {
            Crate c = Crates.CRATES.get(i);
            int x = px + 12, y = top + i * rowH, w = lw, h = rowH - 4;
            boolean on = i == sel, hot = in(mx, my, x, y, w, h);
            Draw.rrect(g, x, y, w, h, 6, on ? Draw.mix(0xFF0C1222, c.colour, 0.18f) : hot ? 0xFF111A30 : 0xFF0B1124);
            if (on) {
                Draw.rrectLine(g, x, y, w, h, 6, c.colour);
                g.fill(x, y + 4, x + 2, y + h - 4, c.colour);
            }
            drawCrate(g, c, x + 18, y + h / 2 + 1, Math.min(26, h), t + i, 0f, 0f);
            if (h >= 22) {
                Draw.bold(g, Draw.fit(c.name.replace(" Crate", ""), w - 60), x + 36, y + h / 2 - 8, on ? TEXT : 0xFFB8C6E4);
                Draw.text(g, c.items.size() + " knives", x + 36, y + h / 2 + 2, FAINT);
            } else {
                Draw.bold(g, Draw.fit(c.name.replace(" Crate", ""), w - 60), x + 36, y + h / 2 - 4, on ? TEXT : 0xFFB8C6E4);
            }
            int n = Crates.held(c.id);
            if (n > 0) {
                String s = "x" + n;
                int bw = Draw.widthBold(s) + 8;
                Draw.rrect(g, x + w - bw - 6, y + h / 2 - 6, bw, 12, 6, c.colour);
                Draw.boldCenter(g, s, x + w - bw / 2 - 6, y + h / 2 - 4, 0xFF0A0A10);
            }
            hit(H_CRATE, i, x, y, w, h);
        }
        Crate c = Crates.CRATES.get(sel);
        // centre: the crate itself
        int cx0 = px + 12 + lw + 12;
        int cw = small ? 128 : Math.max(150, pw * 30 / 100);
        int ccx = cx0 + cw / 2;
        int avail = bottom - top;
        boolean compact = avail < 175;
        int cs = Math.max(compact ? 36 : 44, Math.min(Math.min(cw - 30, 130), Math.round((avail - (compact ? 72 : 100)) / 1.07f)));
        float bob = (float) Math.sin(t * 1.6f) * Math.max(1.5f, cs / 40f);
        int yC = top + 6 + Math.round(cs * 0.62f);
        Draw.glow(g, ccx - cw / 3, yC - cs / 3, cw * 2 / 3, cs * 2 / 3, 30, c.colour, 24, 0.07f);
        drawCrate(g, c, ccx, yC + Math.round(bob), cs, t, 0f, 0f);
        int ny = yC + Math.round(cs * 0.45f) + 4;
        Draw.boldCenterS(g, Draw.fit(c.name.toUpperCase(Locale.ROOT), cw), ccx, ny, c.colour, small ? 1f : 1.15f);
        if (!compact) Draw.textCenter(g, Draw.fit(c.tagline, cw), ccx, ny + 13, SUB);
        else ny -= 13;
        int have = Crates.held(c.id);
        Draw.textCenter(g, Draw.fit(have > 0 ? "You have " + have + "  \u00b7  " + Crates.keys() + " key" + (Crates.keys() == 1 ? "" : "s") : "You have none yet", cw), ccx, ny + 24, have > 0 ? TEXT : FAINT);
        boolean canOpen = have > 0 && Crates.keys() > 0;
        int bw = Math.min(cw - 6, 150);
        button(g, H_OPEN, ccx - bw / 2, ny + 37, bw, 20, canOpen ? "OPEN CRATE" : have > 0 ? "NEEDS A KEY" : "NO CRATE", c.colour, canOpen, true, mx, my, t);
        boolean canBuy = Crates.tokens() >= Crates.KEY_PRICE;
        button(g, H_BUY, ccx - bw / 2, ny + 61, bw, 16, small ? "KEY  \u00b7  " + Crates.KEY_PRICE + " TOKENS" : "BUY KEY  \u00b7  " + Crates.KEY_PRICE + " TOKENS", GOLD, canBuy, false, mx, my, t);
        if (!canBuy && !compact) Draw.textCenter(g, Draw.fit((Crates.KEY_PRICE - Crates.tokens()) + " more tokens for a key", cw), ccx, ny + 80, FAINT);
        // right: contents and odds
        int rx = cx0 + cw + 10, rw = px + pw - 12 - rx;
        Draw.bold(g, "INSIDE", rx, top, TEXT);
        Draw.textRight(g, "rarest first", rx + rw, top, FAINT);
        int cols = 4, gap = 4;
        int tw = (rw - (cols - 1) * gap) / cols;
        int rows = (c.items.size() + cols - 1) / cols;
        int th = Math.max(26, Math.min(52, (bottom - top - 14 - 26 - (rows - 1) * gap) / rows));
        Item hover = null;
        for (int i = 0; i < c.items.size(); i++) {
            Item it = c.items.get(i);
            int x = rx + (i % cols) * (tw + gap), y = top + 12 + (i / cols) * (th + gap);
            card(g, it, x, y, tw, th, t * 0.6f + i, Crates.owns(it.id()), false, false, 1f);
            if (in(mx, my, x, y, tw, th)) hover = it;
        }
        int oy = top + 12 + rows * (th + gap) + 4;
        Rarity[] rs = Rarity.values();
        int per = rw >= 230 ? 3 : 2;
        for (int i = rs.length - 1, k = 0; i >= 0; i--, k++) {
            if (oy + (k / per) * 11 > bottom - 8) break;
            int x = rx + (k % per) * (rw / per), y = oy + (k / per) * 11;
            Draw.rrect(g, x, y + 1, 6, 6, 3, rs[i].colour);
            Draw.text(g, rs[i].label + " " + rs[i].odds(), x + 9, y, 0xFFB0BEDC);
        }
        if (hover != null) tooltip(g, mx, my, hover);
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
    private List<Item> allItems() {
        List<Item> l = new ArrayList<>();
        l.add(new Item(Crates.STARTER, Rarity.MORTAL));
        for (Crate c : Crates.CRATES) l.addAll(c.items);
        return l;
    }

    private void collectionTab(GuiGraphics g, int mx, int my, float t) {
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
        g.enableScissor(px + 10, gy, px + pw - 10, gy + gh);
        Item hover = null;
        int held = KnifeSwing.heldHd();
        for (int i = 0; i < all.size(); i++) {
            int x = px + 12 + (i % cols) * (tw + gap), y = gy + (i / cols) * (th + gap) - Math.round(collScroll);
            if (y + th < gy || y > gy + gh) continue;
            Item it = all.get(i);
            boolean own = Crates.owns(it.id());
            card(g, it, x, y, tw, th, own ? t * 0.6f + i : 0f, false, !own, false, 1f);
            if (own && Crates.hdOf(it.id()) == held) {
                Draw.rrectLine(g, x - 1, y - 1, tw + 2, th + 2, 6, 0xFF6FE3A0);
                Draw.text(g, "HELD", x + 4, y + 4, 0xFF6FE3A0);
            }
            if (in(mx, my, x, y, tw, th) && my >= gy && my < gy + gh) {
                hover = it;
                if (own) hit(H_COLL, i, x, y, tw, th);
            }
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int bh = Math.max(16, gh * gh / (rows * (th + gap)));
            int by = gy + Math.round((gh - bh) * collScroll / maxScroll);
            Draw.rrect(g, px + pw - 9, by, 3, bh, 1, 0xFF2A3E6A);
        }
        if (hover != null) tooltip(g, mx, my, hover);
    }

    // ------------------------------------------------------------------ redeem tab
    private void redeemTab(GuiGraphics g, int mx, int my, float t) {
        int cx = px + pw / 2;
        int top = py + 62;
        Draw.boldCenterS(g, "REDEEM A CODE", cx, top, TEXT, 1.4f);
        Draw.textCenter(g, "Promo codes unlock free crates, each with its own key.", cx, top + 18, SUB);
        int bw = 220;
        int bx = cx - bw / 2, by = py + 90;
        boolean focus = codeBox.isFocused();
        Draw.glow(g, bx, by, bw, 22, 6, GOLD, 6, focus ? 0.25f : 0.08f);
        Draw.rrect(g, bx, by, bw, 22, 6, 0xFF0C1222);
        Draw.rrectLine(g, bx, by, bw, 22, 6, focus ? GOLD : 0xFF2A3E6A);
        hit(H_SKIP, -1, bx, by, bw, 22);
        button(g, H_REDEEM, cx - 60, by + 30, 120, 20, "REDEEM", GOLD, !codeBox.getValue().isBlank(), true, mx, my, t);
        if (!codeMsg.isEmpty()) {
            float a = Math.min(1f, (System.currentTimeMillis() - codeAt) / 200f);
            Draw.textCenter(g, codeMsg, cx, by + 58, Draw.rgba(codeOk ? 0xFF6FE3A0 : 0xFFFF7A7A, a));
        }
        int iy = by + 70, iw = Math.min(pw - 24, 430);
        if (iy + 62 > py + ph - 32) {
            Draw.textCenter(g, Draw.fit("1 token per 5 min of active play  \u00b7  a key costs " + Crates.KEY_PRICE + " tokens  \u00b7  free crate every 90 min", pw - 24), cx, by + 72, FAINT);
            return;
        }
        Draw.rrect(g, cx - iw / 2, iy, iw, 62, 8, 0xFF0B1124);
        Draw.rrectLine(g, cx - iw / 2, iy, iw, 62, 8, 0xFF1C2A48);
        Draw.bold(g, "HOW TO EARN", cx - iw / 2 + 10, iy + 6, GOLD);
        Draw.textRight(g, "opened " + Crates.opened(), cx + iw / 2 - 10, iy + 6, FAINT);
        String[] lines = {"1 Zeus token for every 5 minutes of active play. Standing still earns nothing.",
                "A key costs " + Crates.KEY_PRICE + " tokens. A free random crate drops every 90 minutes of play.",
                "Already own the knife? You get tokens back instead, more for rarer drops."};
        for (int i = 0; i < lines.length; i++) Draw.text(g, Draw.fit("\u2022  " + lines[i], iw - 20), cx - iw / 2 + 10, iy + 19 + i * 13, SUB);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (opening != null) {
            opening.click(mx, my);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] h = hits.get(i);
            if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
            switch (h[0]) {
                case H_TAB -> {
                    if (tab != h[1]) snd(SoundEvents.UI_BUTTON_CLICK, 1.2f, 0.5f);
                    tab = h[1];
                    placeCodeBox();
                    if (tab == 2) setFocused(codeBox);
                }
                case H_CRATE -> {
                    if (sel != h[1]) snd(SoundEvents.UI_BUTTON_CLICK, 1.4f, 0.4f);
                    sel = h[1];
                }
                case H_OPEN -> startOpening(Crates.CRATES.get(sel));
                case H_BUY -> {
                    if (Crates.buyKey()) {
                        snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f);
                        say("Bought a key for " + Crates.KEY_PRICE + " tokens");
                    }
                }
                case H_REDEEM -> redeem();
                case H_COLL -> {
                    Item it = allItems().get(h[1]);
                    equip(it.id());
                }
                case H_CLOSE -> onClose();
                case H_SKIP -> {
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
        if (tab == 1 && opening == null) {
            collScroll -= (float) sy * 30f;
            return true;
        }
        if (tab == 0 && opening == null) {
            sel = Math.floorMod(sel - (int) Math.signum(sy), Crates.CRATES.size());
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
        if (tab == 2 && (key == 257 || key == 335)) {
            redeem();
            return true;
        }
        if (tab == 0 && codeBox != null && !codeBox.isFocused()) {
            if (key == 264) sel = (sel + 1) % Crates.CRATES.size();
            else if (key == 265) sel = Math.floorMod(sel - 1, Crates.CRATES.size());
            else if (key == 257 || key == 32) {
                startOpening(Crates.CRATES.get(sel));
            }
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

    private void startOpening(Crate c) {
        if (Crates.held(c.id) <= 0 || Crates.keys() <= 0) return;
        Crates.Result res = Crates.open(c);
        if (res == null) return;
        opening = new Opening(c, res);
        placeCodeBox();
        snd(SoundEvents.CHEST_LOCKED, 1.3f, 0.7f);
    }

    // ------------------------------------------------------------------ the opening sequence
    private final class Opening {
        static final float BURST = 0.55f, SLIDE = 0.35f, SPIN = 6.2f, SETTLE = 0.7f;
        final Crate crate;
        final Crates.Result res;
        final List<Item> reel = new ArrayList<>();
        final int win;
        final float land;        // where inside the winning card the marker stops (-0.4 .. 0.4)
        final long start = System.nanoTime();
        float skipBy;
        int lastCard = -1;
        long lastTick;
        boolean burstSound, landSound, revealSound;
        final float[][] sparks = new float[70][5];

        Opening(Crate c, Crates.Result r) {
            crate = c;
            res = r;
            Random rnd = new Random();
            for (int i = 0; i < 62; i++) reel.add(Crates.roll(c));
            win = 54;
            reel.set(win, r.item());
            land = (rnd.nextFloat() - 0.5f) * 0.78f;
            for (float[] s : sparks) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float v = 60 + rnd.nextFloat() * 220;
                s[0] = (float) Math.cos(a) * v;
                s[1] = (float) Math.sin(a) * v - 40;
                s[2] = 1 + rnd.nextFloat() * 2.5f;
                s[3] = 0.6f + rnd.nextFloat() * 0.9f;
                s[4] = rnd.nextFloat();
            }
        }

        float t() {
            return (System.nanoTime() - start) / 1e9f + skipBy;
        }

        float revealAt() {
            return BURST + SLIDE + SPIN + SETTLE;
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
            placeCodeBox();
        }

        /** Reel position in cards: eases out like a real wheel, stops on the winner. */
        float pos(float t) {
            float s = t - BURST - SLIDE;
            if (s <= 0) return 0;
            float target = win + land;
            if (s >= SPIN) return target;
            float k = s / SPIN;
            float e = 1f - (float) Math.pow(1f - k, 4.2f);
            return target * e;
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

        void render(GuiGraphics g, int mx, int my, float clock) {
            float t = t();
            hits.clear();
            Rarity rar = res.item().rarity();
            float dim = Math.min(1f, t / 0.25f);
            g.fill(0, 0, width, height, Draw.rgba(0xF0020408, dim));
            int cx = width / 2, cy = height / 2;
            if (t < BURST + SLIDE) {
                // the crate rattles, the lid blows off in a flash of its colour
                float k = Math.min(1f, t / BURST);
                float shake = k < 0.75f ? 2f + 6f * k : 0f;
                float lid = Math.max(0f, (k - 0.7f) / 0.3f);
                if (!burstSound && lid > 0.05f) {
                    burstSound = true;
                    snd(SoundEvents.ENDER_CHEST_OPEN, 1.1f, 0.8f);
                    snd(SoundEvents.FIREWORK_ROCKET_BLAST, 1.3f, 0.5f);
                }
                float fade = 1f - Math.max(0f, (t - BURST) / SLIDE);
                if (fade > 0) {
                    Draw.glow(g, cx - 80, cy - 40, 160, 80, 40, crate.colour, 40, 0.10f * fade + lid * 0.25f * fade);
                    drawCrate(g, crate, cx, cy, 130, clock, lid, shake);
                }
                if (lid > 0) g.fill(0, 0, width, height, Draw.rgba(Draw.lighten(crate.colour, 0.6f), 0.35f * lid * fade));
            }
            if (t >= BURST) reelView(g, t, clock);
            if (t >= revealAt()) reveal(g, mx, my, t - revealAt(), clock, rar);
            else {
                Draw.textCenter(g, "click or press space to skip", cx, height - 18, 0x605E73A0);
            }
        }

        void reelView(GuiGraphics g, float t, float clock) {
            int cw = Math.max(64, Math.min(96, width / 7)), ch = Math.round(cw * 1.22f), gap = 6;
            int step = cw + gap;
            int cy = height / 2 - ch / 2;
            float slide = Math.min(1f, (t - BURST) / SLIDE);
            float ease = 1f - (float) Math.pow(1f - slide, 3);
            float pos = pos(t);
            float after = t - revealAt();
            float out = after > 0 ? Math.min(1f, after / 0.35f) : 0f;
            if (out >= 1f) return;
            // which card is under the marker: a tick each time it changes
            int under = Math.round(pos);
            if (under != lastCard) {
                long n = System.nanoTime();
                if (lastCard >= 0 && n - lastTick > 28_000_000L) {
                    float speed = vel(t);
                    snd(SoundEvents.UI_BUTTON_CLICK, 1.5f + Math.min(0.5f, speed / 60f), 0.22f + Math.min(0.15f, speed / 200f));
                    lastTick = n;
                }
                lastCard = under;
            }
            int rx = 0, rw = width;
            int railY = cy - 14, railH = ch + 28;
            g.fill(rx, railY, rx + rw, railY + railH, Draw.rgba(0xF00A0E1A, ease * (1 - out)));
            g.fill(rx, railY, rx + rw, railY + 1, Draw.rgba(crate.colour, 0.6f * ease * (1 - out)));
            g.fill(rx, railY + railH - 1, rx + rw, railY + railH, Draw.rgba(crate.colour, 0.6f * ease * (1 - out)));
            int ox = Math.round((1f - ease) * width * 0.6f);
            boolean landed = t >= BURST + SLIDE + SPIN;
            float speed = vel(t);
            g.enableScissor(rx, railY, rx + rw, railY + railH);
            int first = (int) Math.floor(pos - (width / 2f) / step) - 1;
            for (int i = Math.max(0, first); i < Math.min(reel.size(), first + width / step + 4); i++) {
                int x = Math.round(width / 2f + (i - pos) * step - cw / 2f) + ox;
                boolean winCard = landed && i == win;
                float a = ease * (1 - out);
                if (landed && !winCard) a *= 1f - 0.65f * Math.min(1f, (t - (BURST + SLIDE + SPIN)) / 0.3f);
                if (winCard) {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 9f);
                    Draw.glow(g, x, cy, cw, ch, 6, res.item().rarity().colour, 12, 0.35f + 0.25f * pulse);
                }
                card(g, reel.get(i), x, cy, cw, ch, i * 1.7f, false, false, false, Math.max(0.35f, a));
                if (a < 0.999f) Draw.rrect(g, x, cy, cw, ch, 5, Draw.rgba(0xFF04060C, 1f - Math.max(0.35f, a)));
            }
            // speed streaks while it races
            if (speed > 8) {
                float k = Math.min(1f, (speed - 8) / 30f);
                Random r = new Random((long) (pos * 3));
                for (int s = 0; s < 14; s++) {
                    int yy = cy + r.nextInt(ch);
                    int len = 40 + r.nextInt(120);
                    int xx = r.nextInt(width);
                    g.fill(xx, yy, xx + len, yy + 1, Draw.rgba(0xFFFFFFFF, 0.10f * k));
                }
            }
            g.disableScissor();
            // the marker
            int mxl = width / 2;
            Draw.glow(g, mxl - 1, railY - 6, 2, railH + 12, 1, GOLD, 6, 0.5f * ease * (1 - out));
            g.fill(mxl - 1, railY - 6, mxl + 1, railY + railH + 6, Draw.rgba(GOLD, ease * (1 - out)));
            for (int k = 0; k < 6; k++) {
                g.fill(mxl - 6 + k, railY - 8 + k, mxl + 6 - k, railY - 7 + k, Draw.rgba(GOLD, ease * (1 - out)));
                g.fill(mxl - 6 + k, railY + railH + 7 - k, mxl + 6 - k, railY + railH + 8 - k, Draw.rgba(GOLD, ease * (1 - out)));
            }
            if (landed && !landSound) {
                landSound = true;
                snd(SoundEvents.NOTE_BLOCK_CHIME, 1.6f, 0.7f);
            }
            Draw.boldCenterS(g, crate.name.toUpperCase(Locale.ROOT), width / 2, railY - 30, Draw.rgba(crate.colour, ease * (1 - out)), 1.3f);
        }

        float vel(float t) {
            return (pos(t + 0.02f) - pos(t - 0.02f)) / 0.04f;
        }

        void reveal(GuiGraphics g, int mx, int my, float u, float clock, Rarity rar) {
            int col = rar.colour;
            int below = 6 + 40 + 6 + 10 + 6 + 20;
            int bhMax = Math.max(60, Math.min(Math.round(height * 0.42f), Math.min(190, height - below - 34)));
            int cx = width / 2, cy = (height - (bhMax + below)) / 2 + bhMax / 2 + 6;
            if (!revealSound) {
                revealSound = true;
                if (rar.ordinal() >= Rarity.TITAN.ordinal()) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    snd(SoundEvents.TOTEM_USE, 1.4f, 0.35f);
                } else if (rar.ordinal() >= Rarity.DIVINE.ordinal()) {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.1f, 0.8f);
                    snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
                } else {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
                }
            }
            float in = Math.min(1f, u / 0.45f);
            float e = 1f - (float) Math.pow(1f - in, 3);
            // white pop as it appears
            if (u < 0.3f) g.fill(0, 0, width, height, Draw.rgba(0xFFFFFFFF, 0.55f * (1f - u / 0.3f)));
            // rarity light, rotating rays
            Draw.glow(g, cx - 120, cy - 80, 240, 160, 80, col, 60, 0.09f * e);
            int rays = 28;
            float rot = clock * 0.25f;
            int len = Math.round(Math.max(width, height) * 0.75f);
            for (int i = 0; i < rays; i++) {
                double a = rot + i * Math.PI * 2 / rays;
                float alpha = (i % 2 == 0 ? 0.10f : 0.05f) * e;
                for (int k = -2; k <= 2; k++) {
                    double aa = a + k * 0.018;
                    Draw.line(g, cx, cy, cx + (float) Math.cos(aa) * len, cy + (float) Math.sin(aa) * len, 3f, Draw.rgba(col, alpha * (1f - Math.abs(k) * 0.25f)));
                }
            }
            // sparks thrown out by the burst
            for (float[] s : sparks) {
                float life = u / s[3];
                if (life >= 1f) continue;
                float x = cx + s[0] * u, y = cy + s[1] * u + 160f * u * u;
                int sz = Math.max(1, Math.round(s[2]));
                g.fill(Math.round(x), Math.round(y), Math.round(x) + sz, Math.round(y) + sz,
                        Draw.rgba(s[4] < 0.5f ? Draw.lighten(col, 0.5f) : 0xFFFFF4D0, 1f - life));
            }
            // the knife: grows in with a little overshoot, then turns slowly
            float pop = in < 1f ? 1f + 0.12f * (float) Math.sin(in * Math.PI) : 1f;
            int bw = Math.round(Math.min(width * 0.62f, 360) * (0.6f + 0.4f * e) * pop);
            int bh = Math.round(bhMax * (0.6f + 0.4f * e) * pop);
            float yaw = -0.35f + 0.55f * (float) Math.sin(u * 1.1f);
            float tilt = 0.10f * (float) Math.sin(u * 0.8f);
            KnifeHDGui.spin(g, Crates.hdOf(res.item().id()), cx - bw / 2, cy - bh / 2 + Math.round(4 * (float) Math.sin(u * 2)), bw, bh, yaw, tilt);
            // banner
            int ty = cy + bhMax / 2 + 6;
            String full = nameOf(res.item().id());
            float ta = Math.min(1f, Math.max(0f, (u - 0.25f) / 0.3f));
            int bw2 = Math.max(Draw.widthBoldS(full.toUpperCase(Locale.ROOT), 1.6f), 160) + 40;
            Draw.rrect(g, cx - bw2 / 2, ty, bw2, 40, 6, Draw.rgba(0xE0080A14, ta), Draw.rgba(Draw.mix(0xFF080A14, col, 0.25f), ta));
            g.fill(cx - bw2 / 2 + 6, ty, cx + bw2 / 2 - 6, ty + 2, Draw.rgba(col, ta));
            Draw.boldCenterS(g, typeName(full).toUpperCase(Locale.ROOT), cx, ty + 6, Draw.rgba(SUB, ta), 0.9f);
            Draw.boldCenterS(g, shortName(full).toUpperCase(Locale.ROOT), cx, ty + 18, Draw.rgba(TEXT, ta), 1.6f);
            // rarity chip and new / duplicate
            String rl = rar.label.toUpperCase(Locale.ROOT);
            float ra = Math.min(1f, Math.max(0f, (u - 0.45f) / 0.25f));
            int rw = Draw.widthBold(rl) + 20;
            int chipY = Math.max(4, cy - bhMax / 2 - 16);
            Draw.rrect(g, cx - rw / 2, chipY, rw, 14, 7, Draw.rgba(col, ra));
            Draw.boldCenter(g, rl, cx, chipY + 3, Draw.rgba(0xFF0A0A12, ra));
            String badge = res.duplicate() ? "DUPLICATE  ·  +" + res.refund() + " TOKEN" + (res.refund() == 1 ? "" : "S") : "NEW!";
            int bc = res.duplicate() ? 0xFF8EA2CC : 0xFF6FE3A0;
            float ba = Math.min(1f, Math.max(0f, (u - 0.6f) / 0.25f));
            float bs = 1f + 0.4f * (1f - ba);
            Draw.boldCenterS(g, badge, cx, ty + 46, Draw.rgba(bc, ba), bs);
            // buttons
            float bt = Math.min(1f, Math.max(0f, (u - 0.8f) / 0.3f));
            if (bt > 0.2f) {
                int y = Math.min(height - 24, ty + 62);
                boolean again = Crates.held(crate.id) > 0 && Crates.keys() > 0;
                int w = 104, gap = 8;
                int n = again ? 3 : 2;
                int x = cx - (n * w + (n - 1) * gap) / 2;
                button(g, H_EQUIP, x, y, w, 20, "EQUIP", col, true, true, mx, my, clock);
                x += w + gap;
                if (again) {
                    button(g, H_AGAIN, x, y, w, 20, "OPEN ANOTHER", crate.colour, true, false, mx, my, clock);
                    x += w + gap;
                }
                button(g, H_DONE, x, y, w, 20, "DONE", 0xFF5E73A0, true, false, mx, my, clock);
            }
        }
    }
}
