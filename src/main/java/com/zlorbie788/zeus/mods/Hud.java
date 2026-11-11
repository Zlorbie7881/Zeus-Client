package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Hud {

    static int panelBg() {
        return Draw.rgba(0xFF0D1018, (float) com.zlorbie788.zeus.ui.UiSettings.HUD_OPACITY.get() / 100f * widgetOpacity());
    }

    static int round(int def) {
        return Math.max(0, Math.min(def + 6, com.zlorbie788.zeus.ui.UiSettings.HUD_ROUND.i() + (def - 4)));
    }

    static int accent() {
        String id = CURRENT_WIDGET.get();
        if (id != null) {
            int custom = com.zlorbie788.zeus.ui.HudLayout.get(id).accent;
            if (custom != 0) return custom;
        }
        return Accent.main();
    }

    private static float widgetOpacity() {
        String id = CURRENT_WIDGET.get();
        return id == null ? 1f : com.zlorbie788.zeus.ui.HudLayout.get(id).opacity;
    }

    static float opacity() {
        return widgetOpacity();
    }

    static void iconDot(GuiGraphics g, int x, int y, int color, float r) {
        g.fill(x - (int) Math.ceil(r) + 1, y - (int) Math.ceil(r) + 1, x + (int) Math.ceil(r), y + (int) Math.ceil(r), color);
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        body(g, x, y, w, h, 1f);
    }

    private static void body(GuiGraphics g, int x, int y, int w, int h, float f) {
        int r = round(4);
        int bg = panelBg();
        Draw.shadow(g, x, y, w, h, 7, 6, 0.30f * f);
        Draw.rrect(g, x, y, w, h, r, fade(Draw.lighten(bg, 0.055f), f), fade(Draw.darken(bg, 0.18f), f));
        Draw.rrect(g, x + 1, y + 1, w - 2, 1, 1, Draw.rgba(0xFFFFFFFF, 0.055f * f));
        if (com.zlorbie788.zeus.ui.UiSettings.HUD_STRIPE.on()) {
            if (!Draw.cheap) Draw.glow(g, x + 1, y + 3, 2, h - 5, 4, accent(), 5, 0.55f * f);
            g.fill(x, y + 3, x + 2, y + h - 3, Draw.rgba(accent(), 0.95f * f));
        }
        Draw.rrectLine(g, x, y, w, h, r, Draw.rgba(accent(), 0.55f * f));
    }

    static int fade(int argb, float f) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    static void panelFade(GuiGraphics g, int x, int y, int w, int h, float fade) {
        body(g, x, y, w, h, Math.max(0f, Math.min(1f, fade)));
    }

    private static final int[] USED = new int[4];

    static int listHeight;
    private static final ThreadLocal<String> CURRENT_WIDGET = new ThreadLocal<>();

    private static final java.util.Map<String, int[]> FRAME_BOUNDS = new java.util.HashMap<>();
    private static final java.util.Map<String, int[]> KNOWN_BOUNDS = new java.util.HashMap<>();

    public static void beginWidget(String id) {
        CURRENT_WIDGET.set(id);
        FRAME_BOUNDS.remove(id);
    }

    public static void endWidget() {
        String id = CURRENT_WIDGET.get();
        if (id != null) {
            int[] b = FRAME_BOUNDS.remove(id);
            if (b != null) KNOWN_BOUNDS.put(id, b);
            else KNOWN_BOUNDS.remove(id);
        }
        CURRENT_WIDGET.remove();
    }

    public static void record(int x, int y, int w, int h) {
        String id = CURRENT_WIDGET.get();
        if (id == null || w <= 0 || h <= 0) return;
        int[] b = FRAME_BOUNDS.get(id);
        if (b == null) FRAME_BOUNDS.put(id, new int[]{x, y, x + w, y + h});
        else {
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.max(b[2], x + w);
            b[3] = Math.max(b[3], y + h);
        }
    }

    public static int[] bounds(String id) {
        return KNOWN_BOUNDS.get(id);
    }

    public static void newFrame() {
        USED[0] = 0;
        USED[1] = 0;
        USED[2] = 0;
        USED[3] = 0;
    }

    static Setting.Choice corner(Module m, int def) {
        return m.addSetting(new Setting.Choice("Corner", "Where it sits. Boxes in the same corner stack on top of each other.", def,
                "Top left", "Top right", "Bottom left", "Bottom right"));
    }

    public static int[] place(int corner, int bw, int bh, int w, int h) {
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner >= 2;
        int base = switch (corner) {
            case 0 -> 30;
            case 1 -> 6 + (listHeight > 0 ? listHeight + 2 : 0);
            case 2 -> 66;
            default -> 6;
        };
        int x = right ? w - 6 - bw : 6;
        int y = bottom ? h - base - USED[corner] - bh : base + USED[corner];
        USED[corner] += bh + 4;
        record(x, y, bw, bh);
        return new int[]{x, y};
    }

    static final int PAD = 9;
    static final int ROW = 20;

    static final int TOP = 23;

    static void caption(GuiGraphics g, int x, int y, String text, int colour) {
        Draw.spaced(g, text.toUpperCase(Locale.ROOT), x, y, colour, 0.8f, 1, true);
    }

    static int captionWidth(String text) {
        return Draw.spacedWidth(text.toUpperCase(Locale.ROOT), 0.8f, 1, true);
    }

    static int header(GuiGraphics g, int x, int y, int w, String title, String count, int colour) {
        caption(g, x + PAD, y + 7, title, Draw.rgba(colour, 0.85f));
        if (count != null) chip(g, x + w - PAD, y + 5, count, colour);
        rule(g, x + PAD, y + 18, w - PAD * 2);
        return TOP;
    }

    static void rule(GuiGraphics g, int x, int y, int w) {
        if (w <= 0) return;
        g.fill(x, y, x + w, y + 1, Draw.rgba(0xFFFFFFFF, 0.07f));
    }

    static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int colour) {
        if (w <= 0 || h <= 0) return;
        int f = Math.max(0, Math.min(w, Math.round(w * Math.max(0f, Math.min(1f, frac)))));
        Draw.rrect(g, x, y, w, h, Math.max(1, h / 2), Draw.rgba(0xFFFFFFFF, 0.09f));
        if (f > 1) {
            if (!Draw.cheap) Draw.glow(g, x, y, f, h, Math.max(1, h / 2), colour, 3, 0.40f);
            Draw.rrect(g, x, y, f, h, Math.max(1, h / 2), Draw.lighten(colour, 0.14f), colour);
        }
    }

    static void slot(GuiGraphics g, int x, int y, int size) {
        Draw.rrect(g, x, y, size, size, 4, Draw.rgba(0xFFFFFFFF, 0.055f));
        Draw.rrectLine(g, x, y, size, size, 4, Draw.rgba(0xFFFFFFFF, 0.075f));
    }

    static void chip(GuiGraphics g, int xRight, int y, String text, int colour) {
        int cw = Draw.widthBold(text) + 12;
        int x = xRight - cw;
        Draw.rrect(g, x, y, cw, 12, 6, Draw.rgba(colour, 0.15f));
        Draw.rrectLine(g, x, y, cw, 12, 6, Draw.rgba(colour, 0.40f));
        Draw.boldCenter(g, text, x + cw / 2, y + 2, Draw.mix(colour, 0xFFFFFFFF, 0.5f));
    }

    static int chipWidth(String text) {
        return Draw.widthBold(text) + 12;
    }

    static int health(float f) {
        return f > 0.5f ? 0xFF5EE6A0 : f > 0.22f ? 0xFFFFC857 : 0xFFFF5C6C;
    }

    public static final class ArmorHud extends Module {
        private final Setting.Choice corner = corner(this, 3).resetOnRev();
        private final Setting.Bool bars = add(new Setting.Bool("Durability numbers", "Show how much is left on every piece.", true));
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

        public ArmorHud() {
            super("armorhud", "Armor HUD", "Your armor and offhand with how much durability is left.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            int rows = 0;
            for (EquipmentSlot s : SLOTS) if (!mc.player.getItemBySlot(s).isEmpty()) rows++;
            if (rows == 0) return;
            boolean wide = bars.on();
            int bw = wide ? 116 : 42;
            int bh = TOP + rows * ROW + 3;
            int[] at = place(corner.index(), bw, bh, w, h);
            panel(g, at[0], at[1], bw, bh);
            caption(g, at[0] + PAD, at[1] + 7, "Armor", Draw.rgba(accent(), 0.85f));
            rule(g, at[0] + PAD, at[1] + 18, bw - PAD * 2);
            int y = at[1] + TOP;
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = mc.player.getItemBySlot(s);
                if (st.isEmpty()) continue;
                int sx = at[0] + 6;
                slot(g, sx, y + 1, 18);
                g.renderItem(st, sx + 1, y + 2);
                if (wide) {
                    int tx = sx + 23;
                    int right = at[0] + bw - 7;
                    Draw.text(g, Draw.fit(st.getHoverName().getString(), Math.max(10, right - tx - 25)), tx, y + 2, Accent.TEXT);
                    if (st.isDamaged()) {
                        int left = st.getMaxDamage() - st.getDamageValue();
                        float f = Math.max(0f, Math.min(1f, left / (float) Math.max(1, st.getMaxDamage())));
                        int c = health(f);
                        Draw.textRight(g, Integer.toString(left), right, y + 2, c);
                        bar(g, tx, y + 13, right - tx, 3, f, c);
                    } else if (st.getCount() > 1) {
                        Draw.textRight(g, "x" + st.getCount(), right, y + 2, Accent.DIM);
                    }
                }
                y += ROW;
            }
        }
    }

    public static final class PotionHud extends Module {
        private final Setting.Choice corner = corner(this, 0);

        public PotionHud() {
            super("potionhud", "Potion HUD", "Your potion effects with the time left.", Category.PANELS);
            anywhere();
        }

        private static String roman(int n) {
            String[] r = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
            return n >= 1 && n < r.length ? " " + r[n] : n >= r.length ? " " + (n + 1) : "";
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            List<String> names = new ArrayList<>();
            List<String> times = new ArrayList<>();
            List<Boolean> good = new ArrayList<>();
            List<Float> left = new ArrayList<>();
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                names.add(e.getEffect().value().getDisplayName().getString() + roman(e.getAmplifier()));
                int t = e.getDuration();
                boolean inf = t < 0 || t > 1_000_000;
                times.add(inf ? "inf" : String.format(Locale.ROOT, "%d:%02d", t / 1200, (t / 20) % 60));
                good.add(e.getEffect().value().isBeneficial());
                left.add(inf ? -1f : t / 20f);
            }
            if (names.isEmpty()) return;
            int bw = captionWidth("Effects") + 26;
            for (int i = 0; i < names.size(); i++) bw = Math.max(bw, Draw.width(names.get(i)) + Draw.width(times.get(i)) + 34);
            int bh = TOP + names.size() * 14 + 3;
            int[] at = place(corner.index(), bw, bh, w, h);
            panel(g, at[0], at[1], bw, bh);
            caption(g, at[0] + PAD, at[1] + 7, "Effects", Draw.rgba(accent(), 0.85f));
            rule(g, at[0] + PAD, at[1] + 18, bw - PAD * 2);
            for (int i = 0; i < names.size(); i++) {
                int y = at[1] + TOP + i * 14;
                boolean ok = good.get(i);
                float secs = left.get(i);
                int tick = ok ? 0xFF5EE6A0 : 0xFFFF5C6C;
                Draw.rrect(g, at[0] + PAD, y + 2, 2, 10, 1, Draw.rgba(tick, 0.85f));
                Draw.text(g, names.get(i), at[0] + PAD + 7, y + 3, ok ? 0xFFC6F7DD : 0xFFFFB6BD);
                int tc = secs < 0f ? Accent.DIM : secs <= 3f ? 0xFFFF5C6C : secs <= 10f ? 0xFFFFC857 : Accent.DIM;
                Draw.textRight(g, times.get(i), at[0] + bw - 7, y + 3, tc);
            }
        }
    }

    public static final class Keystrokes extends Module {
        private final Setting.Choice corner = corner(this, 3);
        private final Setting.Bool mouse = add(new Setting.Bool("Mouse buttons", "Show the left and right button with clicks per second.", true));
        private final long[] left = new long[32];
        private final long[] right = new long[32];
        private boolean wasL;
        private boolean wasR;

        public Keystrokes() {
            super("keystrokes", "Keystrokes", "Shows the keys you hold and your clicks per second.", Category.PANELS);
            anywhere();
        }

        private static void push(long[] ring, long t) {
            for (int i = ring.length - 1; i > 0; i--) ring[i] = ring[i - 1];
            ring[0] = t;
        }

        private static int cps(long[] ring, long now) {
            int n = 0;
            for (long t : ring) if (t != 0 && now - t < 1000) n++;
            return n;
        }

        @Override
        protected void onTick(Minecraft mc) {
            boolean l = mc.options.keyAttack.isDown();
            boolean r = mc.options.keyUse.isDown();
            long now = System.currentTimeMillis();
            if (l && !wasL) push(left, now);
            if (r && !wasR) push(right, now);
            wasL = l;
            wasR = r;
        }

        private void key(GuiGraphics g, int x, int y, int w, int hgt, String label, boolean down) {
            int r = 5;
            if (down) {
                if (!Draw.cheap) Draw.glow(g, x, y, w, hgt, r, accent(), 5, 0.55f);
                Draw.rrect(g, x, y, w, hgt, r, Draw.lighten(accent(), 0.22f), Draw.darken(accent(), 0.10f));
                Draw.rrectLine(g, x, y, w, hgt, r, Draw.rgba(0xFFFFFFFF, 0.38f));
            } else {
                Draw.rrect(g, x, y, w, hgt, r, Draw.rgba(0xFFFFFFFF, 0.052f));
                Draw.rrectLine(g, x, y, w, hgt, r, Draw.rgba(0xFFFFFFFF, 0.075f));
            }
            if (label.isEmpty()) return;
            if (down) Draw.boldCenter(g, label, x + w / 2, y + (hgt - 8) / 2, 0xFFFFFFFF);
            else Draw.textCenter(g, label, x + w / 2, y + (hgt - 8) / 2, Accent.DIM);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            int k = 19;
            int gap = 3;
            int bw = k * 3 + gap * 2 + 12;
            int space = 9;
            boolean ms = mouse.on();
            int bh = 6 + k + gap + k + gap + space + (ms ? gap + 14 : 0) + 6;
            int[] at = place(corner.index(), bw, bh, w, h);
            panel(g, at[0], at[1], bw, bh);
            int x = at[0] + 6;
            int y = at[1] + 6;
            key(g, x + k + gap, y, k, k, "W", mc.options.keyUp.isDown());
            key(g, x, y + k + gap, k, k, "A", mc.options.keyLeft.isDown());
            key(g, x + k + gap, y + k + gap, k, k, "S", mc.options.keyDown.isDown());
            key(g, x + (k + gap) * 2, y + k + gap, k, k, "D", mc.options.keyRight.isDown());
            int sy = y + (k + gap) * 2;
            boolean jump = mc.options.keyJump.isDown();
            key(g, x, sy, bw - 12, space, "", jump);
            g.fill(x + (bw - 12) / 2 - 9, sy + 4, x + (bw - 12) / 2 + 9, sy + 5, jump ? 0xFFFFFFFF : Accent.FAINT);
            if (ms) {
                long now = System.currentTimeMillis();
                int half = (bw - 12 - gap) / 2;
                int by = sy + space + gap;
                key(g, x, by, half, 14, "L " + cps(left, now), mc.options.keyAttack.isDown());
                key(g, x + half + gap, by, half, 14, "R " + cps(right, now), mc.options.keyUse.isDown());
            }
        }
    }

    public static final class TargetHud extends Module {
        private final Setting.Num hold = add(new Setting.Num("Keep showing", "Seconds the last target stays after you look away.", 3, 0, 10, 1, " s"));
        private LivingEntity last;
        private long seen;
        private float shownHealth;
        private float trailHealth;

        public TargetHud() {
            super("targethud", "Target HUD", "Health and armor of whatever you are looking at.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            HitResult r = mc.hitResult;
            long now = System.currentTimeMillis();
            if (r instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
                if (le != last) {
                    shownHealth = le.getHealth();
                    trailHealth = le.getHealth();
                }
                last = le;
                seen = now;
            }
            if (last == null || !last.isAlive() || now - seen > hold.get() * 1000) return;
            LivingEntity e = last;
            float max = Math.max(1f, e.getMaxHealth());
            shownHealth += (e.getHealth() - shownHealth) * 0.18f;
            trailHealth = Math.max(trailHealth, shownHealth);
            trailHealth += (shownHealth - trailHealth) * 0.035f;
            String name = e.getName().getString();
            String hp = String.format(Locale.ROOT, "%.1f", e.getHealth());
            int bw = Math.max(152, Math.max(Draw.widthBold(name) + 58, Draw.width("HP " + hp + " / " + Math.round(max)) + 56));
            int bh = 46;
            int x = w / 2 + 16;
            int y = h / 2 + 16;
            panel(g, x, y, bw, bh);
            float f = Math.max(0f, Math.min(1f, shownHealth / max));
            int col = health(f);
            Draw.bold(g, Draw.fit(name, bw - PAD * 2 - 46), x + PAD, y + 8, Accent.TEXT);
            Draw.bold(g, hp, x + bw - PAD - Draw.widthBold(hp), y + 7, col);
            bar(g, x + PAD, y + 20, bw - PAD * 2, 5, Math.max(0f, Math.min(1f, trailHealth / max)), Draw.rgba(col, 0.30f));
            bar(g, x + PAD, y + 20, bw - PAD * 2, 5, f, col);
            String low = String.format(Locale.ROOT, "%.0f / %.0f", e.getHealth(), max);
            Draw.text(g, low, x + PAD, y + 31, Accent.DIM);
            String right = (e.getArmorValue() > 0 ? "armor " + e.getArmorValue() : "") + (e.getArmorValue() > 0 ? "  " : "")
                    + Math.round(mc.player.distanceTo(e)) + " m";
            Draw.textRight(g, right.trim(), x + bw - PAD, y + 31, Accent.FAINT);
        }
    }

    public static final class Radar extends Module {
        private final Setting.Choice corner = corner(this, 2);
        private final Setting.Num size = add(new Setting.Num("Size", "Diameter of the radar.", 86, 50, 160, 2, " px"));
        private final Setting.Num range = add(new Setting.Num("Range", "How far it sees.", 40, 10, 128, 2, " blocks"));
        private final Setting.Bool players = add(new Setting.Bool("Players", "White dots.", true));
        private final Setting.Bool monsters = add(new Setting.Bool("Monsters", "Red dots.", true));
        private final Setting.Bool animals = add(new Setting.Bool("Animals", "Green dots.", false));
        private final Setting.Bool items = add(new Setting.Bool("Dropped items", "Yellow dots.", false));
        private final Setting.Bool rotate = add(new Setting.Bool("Turn with you", "Up is where you look. Off: up is north.", true));
        private List<Entity> cache = List.of();
        private int ticks;

        public Radar() {
            super("radar", "Radar", "A round map of who is around you.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (++ticks % 3 == 0) cache = Env.entities(mc, range.get());
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            int d = size.i();
            int[] at = place(corner.index(), d, d, w, h);
            int cx = at[0] + d / 2;
            int cy = at[1] + d / 2;
            int r = d / 2;
            Draw.shadow(g, at[0] - 2, at[1] - 2, d + 4, d + 4, r + 2, 6, 0.32f);
            Draw.rrect(g, at[0], at[1], d, d, r, panelBg());
            Draw.ellipse(g, cx, cy, r, r, 0xA00B0E16);
            Draw.ellipse(g, cx, cy, Math.max(2, r / 2), Math.max(2, r / 2), 0x14FFFFFF);
            ring(g, cx, cy, r, Draw.rgba(accent(), 0.75f));
            ring(g, cx, cy, Math.max(2, r / 2), 0x22FFFFFF);
            g.fill(cx - r + 3, cy, cx + r - 3, cy + 1, 0x14FFFFFF);
            g.fill(cx, cy - r + 3, cx + 1, cy + r - 3, 0x14FFFFFF);
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90);
                int x0 = cx + (int) Math.round(Math.cos(a) * (r - 5));
                int y0 = cy + (int) Math.round(Math.sin(a) * (r - 5));
                int x1 = cx + (int) Math.round(Math.cos(a) * (r - 1));
                int y1 = cy + (int) Math.round(Math.sin(a) * (r - 1));
                Draw.line(g, x0, y0, x1, y1, 1.2f, Draw.rgba(0xFFFFFFFF, 0.22f));
            }
            if (!Draw.cheap) {
                double sw = (System.currentTimeMillis() % 3600L) / 3600.0 * 6.2832;
                for (int i = 0; i < 7; i++) {
                    double a = sw - i * 0.055;
                    Draw.line(g, cx, cy, cx + (float) Math.cos(a) * (r - 2), cy + (float) Math.sin(a) * (r - 2), 1f,
                            Draw.rgba(accent(), 0.14f * (1f - i / 7f)));
                }
            }
            double yaw = Math.toRadians(mc.player.getYRot());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double scale = (r - 3) / Math.max(1f, range.get());
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            for (Entity e : cache) {
                if (e == mc.player || !e.isAlive()) continue;
                int col;
                if (e instanceof Player) {
                    if (!players.on()) continue;
                    col = 0xFFFFFFFF;
                } else if (e instanceof Enemy) {
                    if (!monsters.on()) continue;
                    col = 0xFFFF5C6C;
                } else if (e instanceof ItemEntity) {
                    if (!items.on()) continue;
                    col = 0xFFFFC857;
                } else if (e instanceof LivingEntity) {
                    if (!animals.on()) continue;
                    col = 0xFF5EE6A0;
                } else {
                    continue;
                }
                double dx = e.getX() - px;
                double dz = e.getZ() - pz;
                double sx;
                double sy;
                if (rotate.on()) {
                    double ahead = -dx * sin + dz * cos;
                    double leftOf = dx * cos + dz * sin;
                    sx = -leftOf;
                    sy = -ahead;
                } else {
                    sx = dx;
                    sy = dz;
                }
                sx *= scale;
                sy *= scale;
                double len = Math.sqrt(sx * sx + sy * sy);
                if (len > r - 3) {
                    sx = sx / len * (r - 3);
                    sy = sy / len * (r - 3);
                }
                int dotx = cx + (int) Math.round(sx);
                int doty = cy + (int) Math.round(sy);
                Draw.glow(g, dotx - 3, doty - 3, 8, 8, 3, col, 3, 0.6f);
                g.fill(dotx - 1, doty - 1, dotx + 2, doty + 2, 0xFF000000);
                g.fill(dotx - 1, doty - 1, dotx + 1, doty + 1, col);
            }
            int nx;
            int ny;
            if (rotate.on()) {
                nx = cx + (int) Math.round(sin * (r - 8));
                ny = cy + (int) Math.round(cos * (r - 8));
            } else {
                nx = cx;
                ny = cy - (r - 9);
            }
            Draw.textCenter(g, "N", nx, ny - 4, 0xFFFF7A86);
            Draw.glow(g, cx - 2, cy - 3, 6, 6, 2, accent(), 3, 0.6f);
            g.fill(cx - 1, cy - 2, cx + 1, cy + 2, accent());
            g.fill(cx - 2, cy - 1, cx + 2, cy + 1, accent());
        }

        private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
            int n = Math.max(24, r);
            float px = cx + r;
            float py = cy;
            for (int i = 1; i <= n; i++) {
                float a = i / (float) n * 6.2832f;
                float x = cx + (float) Math.cos(a) * r;
                float y = cy + (float) Math.sin(a) * r;
                Draw.line(g, px, py, x, y, 1f, color);
                px = x;
                py = y;
            }
        }
    }

    public static final class Compass extends Module {
        private final Setting.Num width = add(new Setting.Num("Width", "Width of the strip.", 222, 120, 400, 10, " px"));

        public Compass() {
            super("compass", "Compass", "A strip at the top that shows which way you face.", Category.PANELS);
            anywhere();
        }

        private static void fade(GuiGraphics g, int x, int y, int hgt, int w, boolean left, int bg) {
            for (int i = 0; i < w; i++) {
                float f = left ? 1f - i / (float) w : i / (float) w;
                int col = Draw.rgba(bg, 0.95f * f * f);
                if (left) g.fill(x + i, y, x + i + 1, y + hgt, col);
                else g.fill(x + w - 1 - i, y, x + w - i, y + hgt, col);
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            int bw = width.i();
            int x = (w - bw) / 2;
            int y = 6;
            int bh = 19;
            Draw.shadow(g, x, y, bw, bh, 5, 5, 0.35f);
            Draw.rrect(g, x, y, bw, bh, round(5), Draw.lighten(panelBg(), 0.05f), Draw.darken(panelBg(), 0.18f));
            Draw.rrectLine(g, x, y, bw, bh, round(5), Draw.rgba(accent(), 0.5f));
            float yaw = ((mc.player.getYRot() % 360f) + 360f) % 360f;
            String[] marks = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            float pxPerDeg = bw / 140f;
            g.enableScissor(x + 1, y + 1, x + bw - 1, y + bh - 1);
            for (int i = 0; i < 24; i++) {
                float deg = i * 15f;
                float diff = ((deg - yaw + 540f) % 360f) - 180f;
                int sx = Math.round(x + bw / 2f + diff * pxPerDeg);
                if (sx < x || sx > x + bw) continue;
                boolean cardinal = i % 3 == 0;
                boolean center = (i % 3 == 0) && diff > -20f && diff < 20f;
                if (cardinal) {
                    String m = marks[i / 3];
                    boolean north = m.equals("N");
                    Draw.textCenter(g, m, sx, y + 6, north ? 0xFFFF7A86 : center ? Accent.TEXT : Draw.rgba(Accent.TEXT, 0.62f));
                } else {
                    Draw.line(g, sx, y + 7, sx, y + 12, 1f, Draw.rgba(0xFFFFFFFF, 0.18f));
                }
            }
            g.disableScissor();
            fade(g, x + 1, y + 1, bh - 2, 26, true, panelBg());
            fade(g, x + bw - 27, y + 1, bh - 2, 26, false, panelBg());
            g.fill(x + bw / 2 - 1, y + 1, x + bw / 2 + 2, y + 5, accent());
            Draw.rrect(g, x + bw / 2 - 3, y + 1, 7, 2, 1, accent());
            chip(g, x + bw - 5, y + 4, Math.round(yaw) + "°", accent());
        }
    }

    public static final class ItemCounter extends Module {
        private final Setting.Choice corner = corner(this, 3);
        private final Setting.Bool totems = add(new Setting.Bool("Totems", "Totems of Undying.", true));
        private final Setting.Bool crystals = add(new Setting.Bool("End crystals", "End crystals.", true));
        private final Setting.Bool obsidian = add(new Setting.Bool("Obsidian", "Obsidian blocks.", true));
        private final Setting.Bool gapples = add(new Setting.Bool("Golden apples", "Golden apples.", true));
        private final Setting.Bool exp = add(new Setting.Bool("Experience bottles", "Bottles o' enchanting.", false));
        private final Setting.Bool pearls = add(new Setting.Bool("Ender pearls", "Ender pearls.", false));
        private final Setting.Bool anchors = add(new Setting.Bool("Anchors and glowstone", "Respawn anchors and glowstone blocks.", false));

        public ItemCounter() {
            super("itemcounter", "Item Counter", "How many of your important items you carry.", Category.PANELS);
            anywhere();
        }

        private static int count(Minecraft mc, String... ids) {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < 41; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                String name = Names.item(s);
                for (String id : ids) if (name.equals(id)) n += s.getCount();
            }
            return n;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            List<String> lines = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            add(lines, counts, totems.on(), "Totems", count(mc, "totem_of_undying"));
            add(lines, counts, crystals.on(), "Crystals", count(mc, "end_crystal"));
            add(lines, counts, obsidian.on(), "Obsidian", count(mc, "obsidian"));
            add(lines, counts, gapples.on(), "Gapples", count(mc, "golden_apple", "enchanted_golden_apple"));
            add(lines, counts, exp.on(), "Exp bottles", count(mc, "experience_bottle"));
            add(lines, counts, pearls.on(), "Pearls", count(mc, "ender_pearl"));
            add(lines, counts, anchors.on(), "Anchors", count(mc, "respawn_anchor"));
            add(lines, counts, anchors.on(), "Glowstone", count(mc, "glowstone"));
            if (lines.isEmpty()) return;
            int bw = captionWidth("Supply") + 30;
            for (int i = 0; i < lines.size(); i++) {
                bw = Math.max(bw, Draw.width(lines.get(i)) + chipWidth(Integer.toString(counts.get(i))) + 30);
            }
            int bh = TOP + lines.size() * 14 + 3;
            int[] at = place(corner.index(), bw, bh, w, h);
            panel(g, at[0], at[1], bw, bh);
            caption(g, at[0] + PAD, at[1] + 7, "Supply", Draw.rgba(accent(), 0.85f));
            rule(g, at[0] + PAD, at[1] + 18, bw - PAD * 2);
            for (int i = 0; i < lines.size(); i++) {
                int y = at[1] + TOP + i * 14;
                int c = counts.get(i);
                Draw.text(g, lines.get(i), at[0] + PAD, y + 3, c == 0 ? Accent.FAINT : Accent.TEXT);
                chip(g, at[0] + bw - 7, y + 1, Integer.toString(c), c == 0 ? 0xFFFF7A86 : accent());
            }
        }

        private static void add(List<String> lines, List<Integer> counts, boolean on, String name, int n) {
            if (!on) return;
            lines.add(name);
            counts.add(n);
        }
    }
}
