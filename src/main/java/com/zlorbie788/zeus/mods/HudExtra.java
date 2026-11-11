package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

public final class HudExtra {
    private HudExtra() {}

    private static void row(GuiGraphics g, int x, int y, int w, String label, String value, float frac, int colour) {
        Draw.text(g, label, x, y, 0xFF8290A6);
        if (value != null) Draw.textRight(g, value, x + w, y, Draw.mix(colour, 0xFFFFFFFF, 0.35f));
        Hud.bar(g, x, y + 11, w, 3, frac, colour);
    }

    public static final class Vitals extends Module {
        private final Setting.Choice corner = Hud.corner(this, 3);
        private final Setting.Bool numbers = add(new Setting.Bool("Numbers", "The exact amount next to each bar.", true));
        private final Setting.Bool effect = add(new Setting.Bool("Experience", "Your level and the bar towards the next one.", true));

        public Vitals() {
            super("vitals", "Vitals", "Health, hunger, air and experience in one compact card.", Category.PANELS);
            anywhere();
            alias("bars", "status", "hearts");
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null) return;
            Player p = mc.player;
            float hp = p.getHealth();
            float max = Math.max(1f, p.getMaxHealth());
            float abs = p.getAbsorptionAmount();
            int food = p.getFoodData().getFoodLevel();
            int air = p.getAirSupply();
            int airMax = Math.max(1, p.getMaxAirSupply());
            boolean showAir = air < airMax - 1;
            boolean showXp = effect.on();
            int rows = 2 + (abs > 0f ? 1 : 0) + (showAir ? 1 : 0) + (showXp ? 1 : 0);
            int bw = Math.max(118, Hud.captionWidth("Vitals"));
            int bh = Hud.TOP + rows * Hud.ROW - 5;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            Hud.caption(g, at[0] + Hud.PAD, at[1] + 7, "Vitals", Draw.rgba(Hud.accent(), 0.85f));
            Hud.rule(g, at[0] + Hud.PAD, at[1] + 18, bw - Hud.PAD * 2);

            int x = at[0] + Hud.PAD;
            int rw = bw - Hud.PAD * 2;
            int y = at[1] + Hud.TOP;
            int hc = hp / max <= 0.3f ? 0xFFFF4D5E : 0xFFFF6E7A;
            row(g, x, y, rw, "Health", numbers.on() ? String.format(Locale.ROOT, "%.1f", hp) : null, hp / max, hc);
            y += Hud.ROW;
            if (abs > 0f) {
                row(g, x, y, rw, "Absorption", numbers.on() ? String.format(Locale.ROOT, "%.1f", abs) : null,
                        Math.min(1f, abs / 20f), 0xFFFFD84D);
                y += Hud.ROW;
            }
            row(g, x, y, rw, "Hunger", numbers.on() ? Integer.toString(food) : null, food / 20f,
                    food <= 6 ? 0xFFFF8F4D : 0xFFFFB24D);
            y += Hud.ROW;
            if (showAir) {
                row(g, x, y, rw, "Air", numbers.on() ? Integer.toString(air / 20) + "s" : null, air / (float) airMax, 0xFF4DD2FF);
                y += Hud.ROW;
            }
            if (showXp) {
                Draw.text(g, "Level " + p.experienceLevel, x, y, 0xFF8290A6);
                Draw.textRight(g, Math.round(p.experienceProgress * 100f) + "%", x + rw, y, 0xFFA9E8A0);
                Hud.bar(g, x, y + 11, rw, 3, p.experienceProgress, 0xFF6FDC7A);
            }
        }
    }

    public static final class Nearby extends Module {
        private final Setting.Choice corner = Hud.corner(this, 1);
        private final Setting.Num radius = add(new Setting.Num("Radius", "How far around you it counts.", 32, 8, 128, 8, " blocks"));
        private final Setting.Bool quiet = add(new Setting.Bool("Only what is there", "Hide the kinds of thing that are not around at the moment.", true));
        private final Setting.Bool warn = add(new Setting.Bool("Warn on players", "Turns the player count red whenever another player is close.", true));

        private int[] counts = new int[0];
        private String[] labels = new String[0];
        private long builtAt;
        private double builtRadius;

        public Nearby() {
            super("nearby", "Nearby", "Players, mobs and dropped items around you, counted.", Category.PANELS);
            anywhere();
            alias("entities", "around");
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null) return;
            long now = System.nanoTime();
            if (now - builtAt > 250_000_000L || builtRadius != radius.get()) {
                builtAt = now;
                builtRadius = radius.get();
                scan(mc);
            }
            int shown = 0;
            for (int c : counts) if (!quiet.on() || c > 0) shown++;
            if (shown == 0) return;
            int bw = Math.max(104, Hud.captionWidth("Nearby"));
            int bh = Hud.TOP + shown * 14 + 1;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            int players = counts[0];
            Hud.caption(g, at[0] + Hud.PAD, at[1] + 7, "Nearby", Draw.rgba(Hud.accent(), 0.85f));
            Hud.chip(g, at[0] + bw - Hud.PAD, at[1] + 5, Math.round(radius.get()) + "m", Hud.accent());
            Hud.rule(g, at[0] + Hud.PAD, at[1] + 18, bw - Hud.PAD * 2);
            int y = at[1] + Hud.TOP;
            for (int i = 0; i < counts.length; i++) {
                if (quiet.on() && counts[i] == 0) continue;
                int col = i == 0 ? (warn.on() && players > 0 ? 0xFFFF4D5E : 0xFFFF8FA3)
                        : i == 1 ? 0xFFFFB24D : i == 2 ? 0xFF6FDC7A : i == 3 ? 0xFF4DD2FF : 0xFF8B97B3;
                Draw.text(g, labels[i], at[0] + Hud.PAD, y, 0xFF9AA8BE);
                Hud.chip(g, at[0] + bw - Hud.PAD, y - 1, Integer.toString(counts[i]), col);
                y += 14;
            }
        }

        private void scan(Minecraft mc) {
            double r = radius.get();
            double r2 = r * r;
            int[] c = new int[5];
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || e.isSpectator()) continue;
                if (e.distanceToSqr(mc.player) > r2) continue;
                if (e instanceof Player) c[0]++;
                else if (e instanceof Enemy) c[1]++;
                else if (e instanceof ItemEntity) c[3]++;
                else c[2]++;
            }
            labels = new String[] {"Players", "Hostile", "Passive", "Items"};
            counts = new int[] {c[0], c[1], c[2], c[3]};
        }
    }

    public static final class Ticks extends Module {
        private final Setting.Choice corner = Hud.corner(this, 0);
        private final Setting.Bool graph = add(new Setting.Bool("History", "A bar for each of the last forty seconds.", true));

        private static final int SAMPLES = 40;
        private final float[] history = new float[SAMPLES];
        private long windowAt;
        private int windowTicks;
        private long lastGameTime = Long.MIN_VALUE;
        private float tps = 20f;

        public Ticks() {
            super("ticks", "Ticks", "The server's tick rate, so lag shows up as a number instead of a feeling.", Category.PANELS);
            anywhere();
            alias("tps", "lag");
        }

        @Override
        protected void onEnable() {
            windowAt = 0L;
            windowTicks = 0;
            lastGameTime = Long.MIN_VALUE;
            tps = 20f;
            for (int i = 0; i < SAMPLES; i++) history[i] = 20f;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null) return;
            long now = System.nanoTime();
            long time = mc.level.getGameTime();
            if (time != lastGameTime) {
                lastGameTime = time;
                windowTicks++;
            }
            if (windowAt == 0L) windowAt = now;
            long span = now - windowAt;
            if (span >= 1_000_000_000L) {
                float measured = (float) Math.min(20.0, windowTicks * 1e9 / span);
                tps += (measured - tps) * 0.6f;
                System.arraycopy(history, 1, history, 0, SAMPLES - 1);
                history[SAMPLES - 1] = tps;
                windowAt = now;
                windowTicks = 0;
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            int col = tps >= 19.5f ? 0xFF6FDC7A : tps >= 18f ? 0xFFFFC857 : 0xFFFF5C6C;
            int bw = Math.max(112, Hud.captionWidth("Ticks") + 26);
            int bh = Hud.TOP + Hud.ROW * 2 - 4 + (graph.on() ? 20 : 0);
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            Hud.panel(g, at[0], at[1], bw, bh);
            Hud.caption(g, at[0] + Hud.PAD, at[1] + 7, "Ticks", Draw.rgba(Hud.accent(), 0.85f));
            Hud.chip(g, at[0] + bw - Hud.PAD, at[1] + 5, String.format(Locale.ROOT, "%.1f", tps), col);
            Hud.rule(g, at[0] + Hud.PAD, at[1] + 18, bw - Hud.PAD * 2);
            int x = at[0] + Hud.PAD;
            int rw = bw - Hud.PAD * 2;
            int y = at[1] + Hud.TOP;
            Draw.text(g, "Server", x, y, 0xFF8290A6);
            Draw.textRight(g, String.format(Locale.ROOT, "%.1f / 20", tps), x + rw, y, Draw.mix(col, 0xFFFFFFFF, 0.35f));
            Hud.bar(g, x, y + 11, rw, 3, tps / 20f, col);
            y += Hud.ROW;
            Draw.text(g, "Tick interval", x, y, 0xFF8290A6);
            Draw.textRight(g, String.format(Locale.ROOT, "%.0f ms", 1000f / Math.max(1f, tps)), x + rw, y, 0xFFC6D2E8);
            if (graph.on()) {
                int gy = y + 13;
                int step = Math.max(2, rw / SAMPLES);
                for (int i = 0; i < SAMPLES; i++) {
                    float v = history[i];
                    int bar = Math.max(1, Math.round(6f * Math.min(1f, v / 20f)));
                    int bx = x + i * step;
                    if (bx + step - 1 > x + rw) break;
                    g.fill(bx, gy + 6 - bar, bx + step - 1, gy + 6, Draw.rgba(v >= 19.5f ? 0xFF6FDC7A : v >= 18f ? 0xFFFFC857 : 0xFFFF5C6C, 0.85f));
                }
            }
        }
    }
}
