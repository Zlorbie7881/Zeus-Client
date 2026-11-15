package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Sense {
    private Sense() {}

    public record Rated(UUID id, String name, Threat.Result result, double dist, String gear, boolean spectator) {}

    private static final Map<UUID, double[]> LAST = new HashMap<>();
    private static List<Rated> cache = List.of();
    private static long cacheAt;
    private static int cacheRange;

    private static final int[] LEVEL_COLORS = {Accent.OK, Accent.WARN, 0xFFFFA24D, 0xFFFF4D5E};

    public static List<Rated> scan(Minecraft mc, int range) {
        long now = System.nanoTime();
        if (now - cacheAt < 120_000_000L && range <= cacheRange) return cache;
        if (mc.player == null || mc.level == null) return List.of();
        List<Rated> out = new ArrayList<>();
        Map<UUID, double[]> seen = new HashMap<>();
        Player me = mc.player;
        for (var p : mc.level.players()) {
            if (p == me || !p.isAlive()) continue;
            double dist = Math.sqrt(me.distanceToSqr(p));
            if (dist > range) continue;
            double[] prev = LAST.get(p.getUUID());
            double closing = 0;
            if (prev != null) {
                double dt = (now - (long) prev[1]) / 1e9;
                if (dt > 0.05) closing = prev[2] * 0.5 + 0.5 * (prev[0] - dist) / dt;
                else closing = prev[2];
            }
            seen.put(p.getUUID(), new double[] {dist, now, closing});
            int armor = 0;
            boolean elytra = false;
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = p.getItemBySlot(s);
                armor += Gear.armorRank(st);
                if (s == EquipmentSlot.CHEST && Names.item(st).equals("elytra")) elytra = true;
            }
            String main = Names.item(p.getMainHandItem());
            String off = Names.item(p.getOffhandItem());
            var look = p.getLookAngle();
            double dx = me.getX() - p.getX();
            double dy = me.getY() + 1 - (p.getY() + 1.6);
            double dz = me.getZ() - p.getZ();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean aimed = len > 0.1 && (look.x * dx + look.y * dy + look.z * dz) / len > 0.97;
            Threat.Result r = Threat.rate(new Threat.Input(armor, main, off, dist, closing, aimed, p.getHealth(), p.getMaxHealth(), p.isInvisible(), elytra));
            out.add(new Rated(p.getUUID(), p.getName().getString(), r, dist, gearWord(armor, main, off), p.isSpectator()));
        }
        LAST.clear();
        LAST.putAll(seen);
        out.sort((a, b) -> Integer.compare(b.result.score(), a.result.score()));
        cache = out;
        cacheAt = now;
        cacheRange = range;
        return out;
    }

    static String gearWord(int armor, String main, String off) {
        String a = armor >= 240 ? "netherite" : armor >= 170 ? "diamond+" : armor >= 100 ? "iron" : armor > 0 ? "light" : "no armor";
        String w = main.isEmpty() || main.equals("air") ? "" : main.replace('_', ' ');
        return w.isEmpty() ? a : a + ", " + w;
    }

    public static final class ThreatRadar extends Module {
        private final Setting.Choice corner = Hud.corner(this, 3);
        private final Setting.Num range = add(new Setting.Num("Range", "How far away players are rated.", 64, 16, 160, 8, " blocks"));
        private final Setting.Num most = add(new Setting.Num("Most rows", "The most dangerous are kept.", 5, 2, 10, 1, ""));
        private final Setting.Choice from = add(new Setting.Choice("Show from", "Players rated below this are not listed.", 0, Threat.LEVELS));
        private final Setting.Bool reasons = add(new Setting.Bool("Show reasons", "A line under each name that says why it is rated like that.", true));
        private final Setting.Bool alert = add(new Setting.Bool("Warn when someone becomes a danger", "One message when a player first reaches Danger.", true));
        private final Setting.Bool spectators = add(new Setting.Bool("Include spectators", "Spectators cannot fight you, so they are left out unless this is on.", false));
        private final Setting.Num scale = add(new Setting.Num("Size", "The size of the box.", 100, 70, 160, 5, " %"));
        private final Setting.Num opacity = add(new Setting.Num("Background", "How solid the box is.", 88, 30, 100, 5, " %"));
        private final Map<UUID, Long> warned = new HashMap<>();
        private List<Rated> shown = List.of();

        public ThreatRadar() {
            super("threatradar", "Threat Radar", "Rates the players around you by how dangerous they are, from their gear, weapon, distance and aim.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onEnable() {
            warned.clear();
            shown = List.of();
        }

        @Override
        protected void onTick(Minecraft mc) {
            List<Rated> all = scan(mc, range.i());
            List<Rated> out = new ArrayList<>();
            for (Rated r : all) {
                if (r.spectator && !spectators.on()) continue;
                if (r.result.score() < from.index() * 25) continue;
                out.add(r);
                if (alert.on() && r.result.score() >= 75) {
                    long now = System.currentTimeMillis();
                    Long last = warned.get(r.id);
                    if (last == null || now - last > 45_000) {
                        warned.put(r.id, now);
                        Zeus.toast("Danger: " + r.name + " (" + r.result.score() + ", " + Math.round(r.dist) + "m): " + String.join(", ", r.result.reasons().stream().limit(2).toList()), false);
                    }
                }
            }
            shown = out;
        }

        @Override
        public String info() {
            return shown.isEmpty() ? null : Integer.toString(shown.get(0).result.score());
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            List<Rated> list = shown;
            if (list.isEmpty()) return;
            float sc = scale.f() / 100f;
            int n = Math.min(list.size(), most.i());
            boolean why = reasons.on();
            int rh = Math.round((why ? 24 : 15) * sc);
            String title = "Threats · " + list.size();
            int bw = Draw.widthBoldS(title, sc) + Math.round(24 * sc);
            for (int i = 0; i < n; i++) {
                Rated r = list.get(i);
                int rw = Draw.widthS(r.name, sc) + Draw.widthS(r.result.level() + " " + r.result.score() + "  " + Math.round(r.dist) + "m", sc) + Math.round(34 * sc);
                if (why) rw = Math.max(rw, Math.min(Draw.widthS(r.gear + " · " + String.join(", ", r.result.reasons()), sc * 0.8f) + Math.round(16 * sc), Math.round(260 * sc)));
                bw = Math.max(bw, rw);
            }
            bw = Math.min(bw, Math.round(280 * sc));
            int head = Math.round(20 * sc);
            int bh = head + n * rh + 4;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            float bg = opacity.f() / 100f;
            int top = LEVEL_COLORS[indexOf(list.get(0).result.level())];
            Hud.panel(g, at[0], at[1], bw, bh);
            Draw.rrectLine(g, at[0], at[1], bw, bh, 7, Draw.rgba(top, 0.6f));
            Draw.boldS(g, title, at[0] + Math.round(8 * sc), at[1] + Math.round(6 * sc), 0xFFFFFFFF, sc);
            g.fill(at[0] + 8, at[1] + head - 2, at[0] + bw - 8, at[1] + head - 1, Draw.rgba(top, 0.4f));
            int y = at[1] + head + 1;
            for (int i = 0; i < n; i++) {
                Rated r = list.get(i);
                int col = LEVEL_COLORS[indexOf(r.result.level())];
                int x = at[0] + Math.round(8 * sc);
                int ty = y + Math.round(2 * sc);
                g.fill(x - 4, y + 1, x - 2, y + rh - Math.round(3 * sc), Draw.rgba(col, 0.9f));
                Draw.textS(g, r.name, x, ty, 0xFFFFFFFF, sc);
                String right = r.result.level() + " " + r.result.score() + "  " + Math.round(r.dist) + "m";
                Draw.textRightS(g, right, at[0] + bw - Math.round(8 * sc), ty, col, sc);
                if (why) {
                    String line = r.gear + (r.result.reasons().isEmpty() ? "" : " · " + String.join(", ", r.result.reasons()));
                    int max = bw - Math.round(16 * sc);
                    String t = line;
                    while (t.length() > 4 && Draw.widthS(t, sc * 0.8f) > max) t = t.substring(0, t.length() - 2);
                    Draw.textS(g, t.equals(line) ? t : t + "…", x, ty + Math.round(10 * sc), 0xFF8B97B3, sc * 0.8f);
                }
                y += rh;
            }
        }

        private static int indexOf(String level) {
            for (int i = 0; i < Threat.LEVELS.length; i++) if (Threat.LEVELS[i].equals(level)) return i;
            return 0;
        }
    }

}
